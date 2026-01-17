package jnu.econovation.isekai.chat.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.google.genai.errors.ServerException
import com.google.genai.types.Schema
import com.google.genai.types.Type
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.chat.constant.ChatConstants.CONSOLIDATION_COUNT
import jnu.econovation.isekai.chat.constant.ChatConstants.LONG_TERM_MEMORY_SIZE
import jnu.econovation.isekai.chat.constant.ChatConstants.MID_TERM_MEMORY_SIZE
import jnu.econovation.isekai.chat.constant.ChatConstants.SHORT_TERM_MEMORY_SIZE
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.dto.internal.ChatHistoryDTO
import jnu.econovation.isekai.chat.dto.internal.ShortTermMemoryDTO
import jnu.econovation.isekai.chat.dto.internal.SummarizeDTO
import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.model.entity.ConsolidatedMemory
import jnu.econovation.isekai.chat.model.vo.Speaker
import jnu.econovation.isekai.chat.service.internal.ChatDataService
import jnu.econovation.isekai.chat.service.internal.ConsolidatedMemoryDataService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import jnu.econovation.isekai.prompt.config.PromptConfig
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
    private val consolidatedMemoryService: ConsolidatedMemoryDataService,
    private val memberService: MemberService,
    private val characterService: CharacterCoordinateService
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
    suspend fun save(
        hostMemberId: Long,
        characterDTO: CharacterDTO,
        chatDTO: ChatDTO
    ) {
        logger.info { "채팅 기록 저장 중 -> $chatDTO" }

        val hostMember = memberService.getEntity(hostMemberId)
            ?: throw InternalServerException(IllegalStateException("아이디가 ${hostMemberId}인 회원을 찾지 못함"))

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

        logger.info { "채팅 기록 저장 성공 -> $chatDTO" }
    }

    @Transactional(readOnly = true)
    fun getShortTermMemory(
        characterDTO: CharacterDTO,
        hostMemberId: Long
    ): ShortTermMemoryDTO? {
        val recentChat = chatService
            .getRecentChats(characterDTO, hostMemberId, SHORT_TERM_MEMORY_SIZE)
            .map { ChatHistoryDTO.from(it) }

        if (recentChat.isEmpty()) {
            return null
        }

        return ShortTermMemoryDTO(
            startTime = recentChat.first().chattedAt,
            endTime = recentChat.last().chattedAt,
            content = recentChat.joinToString("\n____________\n")
        )
    }

    @Transactional(readOnly = true)
    fun getMidTermMemory(
        characterDTO: CharacterDTO,
        hostMemberId: Long
    ): String {
        val midTermMemory = consolidatedMemoryService.findRecentMemories(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId,
            limit = MID_TERM_MEMORY_SIZE
        )

        return midTermMemory.joinToString("\n_____________________\n") { it.summary }
    }

    @Transactional(readOnly = true)
    suspend fun getLongTermMemory(
        characterDTO: CharacterDTO,
        hostMemberId: Long,
        searchText: String,
    ): String {
        val embedding = try {
            embedVector(searchText, EMBEDDING_PLANS.planA)
        } catch (_: ServerException) {
            logger.warn { "Retry exhausted로 인한 임베딩 교체 : ${EMBEDDING_PLANS.planA} -> ${EMBEDDING_PLANS.planB}" }
            embedVector(searchText, EMBEDDING_PLANS.planB)
        }

        val longTermMemory = consolidatedMemoryService.findSimilarMemories(
            characterDTO = characterDTO,
            hostMemberId = hostMemberId,
            embedding = embedding,
            limit = LONG_TERM_MEMORY_SIZE
        )

        return longTermMemory.joinToString("\n_____________________\n") { it.summary }
    }


    private suspend fun consolidate(
        characterDTO: CharacterDTO,
        hostMember: Member,
        counter: RedisAtomicInteger
    ) {
        val shortTermMemoryDTO: ShortTermMemoryDTO = getShortTermMemory(
            characterDTO = characterDTO,
            hostMemberId = hostMember.id
        )
            ?: throw InternalServerException(IllegalStateException("consolidation count 가 ${CONSOLIDATION_COUNT}이지만, recent chat 이 empty임"))

        val summaryPrefix = "[${shortTermMemoryDTO.startTime} ~ ${shortTermMemoryDTO.endTime}]"

        try {
            val (summarizeDTO, embedding) = summarizeAndEmbed(
                shortTermMemoryDTO.content,
                CONSOLIDATION_PLANS
            )

            val characterEntity = characterService.getCharacterEntity(characterDTO.id)
                ?: throw InternalServerException(cause = IllegalStateException("아이디가 ${characterDTO.id}인 캐릭터를 찾지 못함"))

            consolidatedMemoryService.save(
                ConsolidatedMemory.builder()
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