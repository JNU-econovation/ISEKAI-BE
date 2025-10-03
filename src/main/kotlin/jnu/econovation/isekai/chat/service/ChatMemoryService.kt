package jnu.econovation.isekai.chat.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.google.genai.errors.ServerException
import com.google.genai.types.Schema
import com.google.genai.types.Type
import jnu.econovation.isekai.chat.constant.ChatConstants.CONSOLIDATION_COUNT
import jnu.econovation.isekai.chat.constant.ChatConstants.LONG_TERM_MEMORY_COUNT
import jnu.econovation.isekai.chat.dto.internal.ChatDTO
import jnu.econovation.isekai.chat.dto.internal.ChatHistoryDTO
import jnu.econovation.isekai.chat.dto.internal.SummarizeDTO
import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.model.entity.LongTermMemory
import jnu.econovation.isekai.chat.model.vo.Speaker
import jnu.econovation.isekai.chat.service.internal.ChatDataService
import jnu.econovation.isekai.chat.service.internal.LongTermMemoryDataService
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.gemini.enums.GeminiModel
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.persona.config.PromptConfig
import jnu.econovation.isekai.persona.model.entity.Persona
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
    private val longTermMemoryService: LongTermMemoryDataService,
    private val geminiClient: GeminiClient,
    private val connectionFactory: RedisConnectionFactory,
    private val promptConfig: PromptConfig,
    private val mapper: ObjectMapper
) {

    private companion object {
        val LOGGER = KotlinLogging.logger {}

        val CONSOLIDATION_PLANS = ConsolidationPlans(
            embeddingPlanA = GeminiModel.GEMINI_EMBEDDING_001,
            embeddingPlanB = GeminiModel.TEXT_EMBEDDING_004,
            summarizePlanA = GeminiModel.GEMINI_2_5_FLASH,
            summarizePlanB = GeminiModel.GEMINI_2_5_PRO
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
    suspend fun save(hostMember: Member, persona: Persona, chatDTO: ChatDTO) {
        val inputChat = Chat.builder()
            .hostMember(hostMember)
            .persona(persona)
            .speaker(Speaker.USER)
            .content(chatDTO.input)
            .build()

        val outputChat = Chat.builder()
            .hostMember(hostMember)
            .persona(persona)
            .speaker(Speaker.BOT)
            .content(chatDTO.output)
            .build()

        chatService.save(inputChat)
        chatService.save(outputChat)

        val counter = RedisAtomicInteger("${hostMember.id}:chatCounter", connectionFactory)
        val count = counter.incrementAndGet()

        if (count >= CONSOLIDATION_COUNT) {
            consolidate(hostMember, counter, CONSOLIDATION_PLANS)
        }
    }

    @Transactional(readOnly = true)
    fun getMemory(persona: Persona, hostMember: Member) {
/*
        val (_, shortTermMemory) = getShortTermMemory(persona, hostMember)
        val longTermMemory = longTermMemoryService.findSimilarMemories(
            embedVector(
                shortTermMemory,
                GeminiModel.GEMINI_EMBEDDING_001
            ), 10
        )
*/
    }

    private suspend fun consolidate(
        hostMember: Member,
        counter: RedisAtomicInteger,
        plans: ConsolidationPlans
    ) {
        val (recentChat, shortTermMemory) = getShortTermMemory(hostMember)
        val startTime = recentChat.first().chattedAt
        val endTime = recentChat.last().chattedAt
        val summaryPrefix = "[${startTime} ~ ${endTime}]"

        val summarizeDTO = try {
            summarize(shortTermMemory, plans.summarizePlanA)
        } catch (_: ServerException) {
            LOGGER.warn("Retry exhausted로 인한 summarizer 교체 : ${plans.summarizePlanA} -> ${plans.summarizePlanB}")
            summarize(shortTermMemory, plans.summarizePlanB)
        }

        val vectorEmbeddings = try {
            embedVector(summaryPrefix + summarizeDTO.summary, plans.embeddingPlanA)
        } catch (_: ServerException) {
            LOGGER.warn { "Retry exhausted로 인한 임베딩 교체 : ${plans.embeddingPlanA} -> ${plans.embeddingPlanB}" }
            embedVector(summaryPrefix + summarizeDTO.summary, plans.embeddingPlanB)
        }

        longTermMemoryService.save(
            LongTermMemory.builder()
                .summary(summarizeDTO.summary)
                .hostMember(hostMember)
                .embedding(vectorEmbeddings)
                .build()
        )

        counter.set(0)
    }

    private fun getShortTermMemory(
        persona: Persona,
        hostMember: Member
    ): Pair<List<ChatHistoryDTO>, String> {
        val recentChat = chatService
            .getRecentChats(persona, hostMember, LONG_TERM_MEMORY_COUNT)
            .map { ChatHistoryDTO.from(it) }

        val shortTermMemory = recentChat.joinToString("\n\n") { it.content }
        return Pair(recentChat, shortTermMemory)
    }

    private suspend fun summarize(shortTermMemory: String, model: GeminiModel): SummarizeDTO {
        val response = getSummaryResponse(shortTermMemory, model)

        return mapper.readValue(response, SummarizeDTO::class.java)
    }

    //TODO: Exponential Backoff
    @Retryable(
        value = [ServerException::class],
        maxAttempts = 4,
        backoff = Backoff(delay = 3000)
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
        maxAttempts = 4,
        backoff = Backoff(delay = 3000)
    )
    private suspend fun embedVector(text: String, model: GeminiModel) =
        geminiClient.getEmbedding(text, model).first().values().get().toFloatArray()

}

private data class ConsolidationPlans(
    val summarizePlanA: GeminiModel,
    val summarizePlanB: GeminiModel,
    val embeddingPlanA: GeminiModel,
    val embeddingPlanB: GeminiModel
)