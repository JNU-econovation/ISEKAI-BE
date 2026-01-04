package jnu.econovation.isekai.prompt.service

import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.prompt.config.PromptConfig
import org.springframework.stereotype.Service

@Service
class PromptService(
    val config: PromptConfig,
) {

    private val promptPrefix = """
        ${config.memory}
        
        
        [우선 임무]
        
    """.trimIndent()


    fun getPrompt(character: CharacterDTO): String {
        return promptPrefix + character.persona.value
    }

}