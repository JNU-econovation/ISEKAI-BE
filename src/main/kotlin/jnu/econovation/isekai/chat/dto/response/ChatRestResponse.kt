package jnu.econovation.isekai.chat.dto.response

import jnu.econovation.isekai.chat.model.entity.Chat

data class ChatRestResponse(
    val speaker: String,
    val content: String
) {
    companion object {
        fun from(chat: Chat) : ChatRestResponse {
            return ChatRestResponse(
                speaker = chat.speaker.name,
                content = chat.content
            )
        }
    }
}
