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
import jnu.econovation.isekai.prompt.config.PromptConfig
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

    companion object {
        private val GEMINI_SUMMARY_SCHEMA = Schema.builder()
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
    suspend fun save(member: Member, chatDTO: ChatDTO) {
        val inputChat = Chat.builder()
            .hostMember(member)
            .speaker(Speaker.USER)
            .content(chatDTO.input)
            .build()

        val outputChat = Chat.builder()
            .hostMember(member)
            .speaker(Speaker.BOT)
            .content(chatDTO.output)
            .build()

        chatService.save(inputChat)
        chatService.save(outputChat)

        val counter = RedisAtomicInteger("${member.id}:chatCounter", connectionFactory)
        val count = counter.incrementAndGet()

        if (count >= CONSOLIDATION_COUNT) {
            consolidate(member, counter)
        }
    }

    private suspend fun consolidate(
        member: Member,
        counter: RedisAtomicInteger
    ) {
        val recentChatString = chatService
            .getRecentChats(member, LONG_TERM_MEMORY_COUNT)
            .map { ChatHistoryDTO.from(it) }
            .joinToString("\n\n") { it.toString() }

        val summaryText = geminiClient.getTextResponse(
            prompt = promptConfig.summarize,
            request = recentChatString,
            schema = GEMINI_SUMMARY_SCHEMA
        )

        val summarizeDTO = mapper.readValue(summaryText, SummarizeDTO::class.java)

        val vectorEmbeddings = try {
            embedVector(summarizeDTO.summary, GeminiModel.GEMINI_EMBEDDING_001)
        } catch (_: ServerException) {
            embedVector(summarizeDTO.summary, GeminiModel.TEXT_EMBEDDING_004)
        }

        longTermMemoryService.save(
            LongTermMemory.builder()
                .summary(summarizeDTO.summary)
                .hostMember(member)
                .embedding(vectorEmbeddings)
                .build()
        )

        counter.set(0)
    }

    @Retryable(
        value = [ServerException::class],
        maxAttempts = 4,
        backoff = Backoff(delay = 3000)
    )
    private suspend fun summarize(recentChatString: String): String {
        return geminiClient.getTextResponse(
            prompt = promptConfig.summarize,
            request = recentChatString,
            schema = GEMINI_SUMMARY_SCHEMA
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