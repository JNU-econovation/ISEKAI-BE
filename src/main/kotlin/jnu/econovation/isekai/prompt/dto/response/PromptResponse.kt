package jnu.econovation.isekai.prompt.dto.response

import jnu.econovation.isekai.prompt.model.entity.Prompt

data class PromptResponse(
    val author: String,
    val personaName: String,
    val content: String,
    val isPublic: Boolean
) {
    companion object {
        fun from(prompt: Prompt): PromptResponse {
            return PromptResponse(
                author = prompt.author.email.value,
                personaName = prompt.personaName.value,
                content = prompt.content.value,
                isPublic = prompt.isPublic
            )
        }
    }
}