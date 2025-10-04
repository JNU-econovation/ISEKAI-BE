package jnu.econovation.isekai.prompt.service

import jnu.econovation.isekai.persona.model.entity.Persona
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


    fun getPrompt(persona: Persona): String {
        return promptPrefix + persona.content.value
    }

}