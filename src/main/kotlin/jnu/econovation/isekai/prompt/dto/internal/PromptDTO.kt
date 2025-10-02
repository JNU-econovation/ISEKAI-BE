package jnu.econovation.isekai.prompt.dto.internal

import jnu.econovation.isekai.common.util.TimeUtil
import jnu.econovation.isekai.prompt.dto.response.PromptResponse
import jnu.econovation.isekai.prompt.model.entity.Prompt

data class PromptDTO(
    val id: Long,
    val author: String,
    val personaName: String,
    val content: String,
    val isPublic: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(prompt: Prompt): PromptDTO {
            return PromptDTO(
                id = prompt.id,
                author = prompt.author.email.value,
                personaName = prompt.personaName.value,
                content = prompt.content.value,
                isPublic = prompt.isPublic,
                createdAt = TimeUtil.formatToSimpleString(prompt.createdAt)
            )
        }
    }

    fun toResponseDTO(): PromptResponse {
        return PromptResponse(
            id = id,
            author = author,
            personaName = personaName,
            content = content,
            isPublic = isPublic,
            createdAt = createdAt
        )
    }
}