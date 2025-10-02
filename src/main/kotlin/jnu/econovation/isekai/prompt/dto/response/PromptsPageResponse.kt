package jnu.econovation.isekai.prompt.dto.response

import jnu.econovation.isekai.common.util.TimeUtil
import jnu.econovation.isekai.prompt.model.entity.Prompt

data class PromptsPageResponse(
    val id: Long,
    val author: String,
    val personaName: String,
    val isPublic: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(prompt: Prompt): PromptsPageResponse {
            return PromptsPageResponse(
                id = prompt.id,
                author = prompt.author.email.value,
                personaName = prompt.personaName.value,
                isPublic = prompt.isPublic,
                createdAt = TimeUtil.formatToSimpleString(prompt.createdAt)
            )
        }
    }
}