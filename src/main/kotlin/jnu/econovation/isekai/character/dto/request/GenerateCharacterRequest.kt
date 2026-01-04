package jnu.econovation.isekai.character.dto.request

data class GenerateCharacterRequest(
    val uuid: String,
    val prompt: String
)