package jnu.econovation.isekai.chat.dto.internal

import jnu.econovation.isekai.chat.model.entity.Chat
import jnu.econovation.isekai.chat.model.vo.Speaker
import jnu.econovation.isekai.common.util.TimeUtil
import java.time.ZonedDateTime

data class ChatHistoryDTO(
    val speaker: Speaker,
    val content: String,
    val chattedAt: ZonedDateTime
) {

    override fun toString(): String {
        return "[${TimeUtil.formatToSimpleString(chattedAt)}] ${speaker.name}: $content"
    }

    companion object {
        fun from(chat: Chat): ChatHistoryDTO {
            return ChatHistoryDTO(
                speaker = chat.speaker,
                content = chat.content,
                chattedAt = TimeUtil.convertInstantToZonedDateTime(chat.createdAt)
            )
        }
    }
}
