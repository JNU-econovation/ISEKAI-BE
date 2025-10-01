package jnu.econovation.isekai.prompt.dto.internal

import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.prompt.model.entity.Prompt

data class PromptDTO(
    val author: MemberInfoDTO,
    val personaName: String,
    val content: String,
    val isPublic: Boolean
) {
    companion object {
        fun from(prompt: Prompt) = PromptDTO(
            author = MemberInfoDTO.from(prompt.author),
            personaName = prompt.personaName.value,
            content = prompt.content.value,
            isPublic = prompt.isPublic
        )
    }
}