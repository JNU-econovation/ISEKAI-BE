package jnu.econovation.isekai.chat.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.google.genai.errors.ServerException
import com.google.genai.types.Schema
import com.google.genai.types.Type
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterService
import jnu.econovation.isekai.chat.constant.ChatConstants.CONSOLIDATION_COUNT
import jnu.econovation.isekai.chat.constant.ChatConstants.LONG_TERM_MEMORY_LIMIT
import jnu.econovation.isekai.chat.constant.ChatConstants.SHORT_TERM_MEMORY_SIZE
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.dto.internal.ChatHistoryDTO
import jnu.econovation.isekai.chat.dto.internal.SummarizeDTO
import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import jnu.econovation.isekai.chat.model.vo.Speaker
import jnu.econovation.isekai.chat.service.internal.ChatDataService
import jnu.econovation.isekai.chat.service.internal.LongTermMemoryDataService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.dto.client.request.GeminiInput
import jnu.econovation.isekai.member.constant.MemberConstants.MASTER_EMAIL
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.prompt.config.PromptConfig
import jnu.econovation.isekai.rtzr.dto.client.response.RtzrSttResponse
import jnu.econovation.isekai.rtzr.service.RtzrSttService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.support.atomic.RedisAtomicInteger
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatMemoryService(
    private val chatService: ChatDataService,
    private val geminiClient: GeminiClient,
    private val connectionFactory: RedisConnectionFactory,
    private val promptConfig: PromptConfig,
    private val mapper: ObjectMapper,
    private val rtzrSttService: RtzrSttService,
    private val longTermMemoryService: LongTermMemoryDataService,
    private val memberService: MemberService,
    private val characterService: CharacterService
) {

    private companion object {
        val logger = KotlinLogging.logger {}

        val SUMMARIZE_PLANS = SummarizePlan(
            planA = GeminiModel.GEMINI_2_5_FLASH,
            planB = GeminiModel.GEMINI_2_5_PRO
        )

        val EMBEDDING_PLANS = EmbeddingPlan(
            planA = GeminiModel.GEMINI_EMBEDDING_001,
            planB = GeminiModel.TEXT_EMBEDDING_004
        )

        val CONSOLIDATION_PLANS = ConsolidationPlan(
            summarizePlan = SUMMARIZE_PLANS,
            embeddingPlan = EMBEDDING_PLANS
        )

        val GEMINI_SUMMARY_SCHEMA: Schema = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(
                ImmutableMap.of(
                    "summary",
                    Schema.builder().type(Type.Known.STRING)
                        .build(),
                    "topics",
                    Schema.builder()
                        .type(Type.Known.ARRAY)
                        .items(Schema.builder().type(Type.Known.STRING).build())
                        .build()
                )
            ).required("summary", "topics").build()
    }

    @Transactional
    suspend fun save(characterDTO: CharacterDTO, chatDTO: ChatDTO) {
        logger.info { "채팅 기록 저장 중 -> $chatDTO" }
        val hostMember = memberService.findByEmailEntity(MASTER_EMAIL)
            ?: throw InternalServerException(IllegalStateException("master member not found"))

        val (inputChat, outputChat) = buildInputChatAndOutputChat(hostMember, characterDTO, chatDTO)

        chatService.save(inputChat)
        chatService.save(outputChat)

        val counter = RedisAtomicInteger("${hostMember.id}:chatCounter", connectionFactory)
        val count = counter.incrementAndGet()

        if (count >= CONSOLIDATION_COUNT) {
            logger.info { "chatting count >= $CONSOLIDATION_COUNT -> consolidation 진행 중" }
            consolidate(characterDTO, hostMember, counter)
            counter.set(0)
        }
    }

    @Transactional(readOnly = true)
    suspend fun findMemoriesFromVoiceStream(
        rtzrReadySignal: CompletableDeferred<Unit>,
        voiceChunk: Flow<ByteArray>,
        characterDTO: CharacterDTO,
        hostMemberId: Long,
    ): Flow<GeminiInput.Context> {
        val sttResultFlow: Flow<RtzrSttResponse> = rtzrSttService
            .stt(voiceChunk, rtzrReadySignal)
            .filter { it.final }
            .onEach { logger.info { "rtzr stt 결과 -> ${it.alternatives.first().text}" } }

        val (_, shortTermMemory) = getShortTermMemory(characterDTO, hostMemberId)

        return sttResultFlow
            .map { it.alternatives.first().text }
            .filter { it.isNotBlank() }
            .map {
                val embedding = try {
                    embedVector(it, EMBEDDING_PLANS.planA)
                } catch (_: ServerException) {
                    logger.warn { "Retry exhausted로 인한 임베딩 교체 : ${EMBEDDING_PLANS.planA} -> ${EMBEDDING_PLANS.planB}" }
                    embedVector(it, EMBEDDING_PLANS.planB)
                }

                val longTermMemory = getLongTermMemory(characterDTO, hostMemberId, embedding)

                GeminiInput.Context(shortTermMemory, longTermMemory)
            }
    }

    private suspend fun consolidate(
        characterDTO: CharacterDTO,
        hostMember: Member,
        counter: RedisAtomicInteger
    ) {
        val (recentChat, shortTermMemory) = getShortTermMemory(characterDTO, hostMember.id)

        if (recentChat.isEmpty()) {
            throw InternalServerException(IllegalStateException("consolidation count 가 ${CONSOLIDATION_COUNT}이지만, recent chat 이 empty임"))
        }

        val startTime = recentChat.first().chattedAt
        val endTime = recentChat.last().chattedAt
        val summaryPrefix = "[${startTime} ~ ${endTime}]"

        try {
            val (summarizeDTO, embedding) = summarizeAndEmbed(
                shortTermMemory,
                CONSOLIDATION_PLANS
            )

            val characterEntity = characterService.getCharacterEntity(characterDTO.id)
                ?: throw InternalServerException(cause = IllegalStateException("아이디가 ${characterDTO.id}인 캐릭터를 찾지 못함"))

            longTermMemoryService.save(
                LongTermMemory.builder()
                    .character(characterEntity)
                    .summary(summaryPrefix + summarizeDTO.summary)
                    .hostMember(hostMember)
                    .embedding(embedding)
                    .build()
            )

        } catch (e: Exception) {
            logger.error(e) { "Consolidation 작업 중 예외 발생" }
            counter.set(0)
        }
    }


    private fun buildInputChatAndOutputChat(
        hostMember: Member,
        characterDTO: CharacterDTO,
        chatDTO: ChatDTO
    ): Pair<Chat, Chat> {
        val character = characterService.getCharacterEntity(characterDTO.id)

        val inputChat = Chat.builder()
            .hostMember(hostMember)
            .character(character)
            .speaker(Speaker.USER)
            .content(chatDTO.input)
            .build()

        val outputChat = Chat.builder()
            .hostMember(hostMember)
            .character(character)
            .speaker(Speaker.BOT)
            .content(chatDTO.output)
            .build()

        return Pair(inputChat, outputChat)
    }

    private suspend fun summarizeAndEmbed(
        shortTermMemory: String,
        plans: ConsolidationPlan
    ): Pair<SummarizeDTO, FloatArray> {

        val summarizeDTO = try {
            summarize(shortTermMemory, plans.summarizePlan.planA)
        } catch (_: ServerException) {
            logger.warn("Retry exhausted로 인한 summarizer 교체 : ${plans.summarizePlan.planA} -> ${plans.summarizePlan.planB}")
            summarize(shortTermMemory, plans.summarizePlan.planB)
        }

        val vectorEmbeddings = try {
            embedVector(summarizeDTO.summary, plans.embeddingPlan.planA)
        } catch (_: ServerException) {
            logger.warn { "Retry exhausted로 인한 임베딩 교체 : ${plans.embeddingPlan.planA} -> ${plans.embeddingPlan.planB}" }
            embedVector(summarizeDTO.summary, plans.embeddingPlan.planB)
        }

        return summarizeDTO to vectorEmbeddings
    }

    private fun getShortTermMemory(
        characterDTO: CharacterDTO,
        hostMemberId: Long
    ): Pair<List<ChatHistoryDTO>, String> {
        val recentChat = chatService
            .getRecentChats(characterDTO, hostMemberId, SHORT_TERM_MEMORY_SIZE)
            .map { ChatHistoryDTO.from(it) }

        val shortTermMemory = recentChat.joinToString("\n\n") { it.content }
        return Pair(recentChat, shortTermMemory)
    }

    private fun getLongTermMemory(
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        embedding: FloatArray
    ): String {
        val longTermMemory = longTermMemoryService.findSimilarMemories(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId,
            embedding = embedding,
            limit = LONG_TERM_MEMORY_LIMIT
        )

        return longTermMemory.joinToString("\n\n") { it.summary }
    }

    private suspend fun summarize(shortTermMemory: String, model: GeminiModel): SummarizeDTO {
        val response = getSummaryResponse(shortTermMemory, model)

        return mapper.readValue(response, SummarizeDTO::class.java)
    }

    //todo: Jitter 추가
    @Retryable(
        value = [ServerException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    private suspend fun getSummaryResponse(
        recentChatString: String,
        model: GeminiModel
    ): String {
        return geminiClient.getTextResponse(
            prompt = promptConfig.summarize,
            request = recentChatString,
            schema = GEMINI_SUMMARY_SCHEMA,
            model = model
        )
    }

    @Retryable(
        value = [ServerException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 2000, multiplier = 2.0)
    )
    private suspend fun embedVector(text: String, model: GeminiModel) =
        geminiClient.getEmbedding(text, model).first().values().get().toFloatArray()

}

private data class ConsolidationPlan(
    val summarizePlan: SummarizePlan,
    val embeddingPlan: EmbeddingPlan
)

private data class SummarizePlan(
    val planA: GeminiModel,
    val planB: GeminiModel
)

private data class EmbeddingPlan(
    val planA: GeminiModel,
    val planB: GeminiModel
)