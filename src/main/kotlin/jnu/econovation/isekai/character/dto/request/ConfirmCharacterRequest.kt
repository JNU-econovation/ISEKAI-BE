package jnu.econovation.isekai.character.dto.request

data class ConfirmCharacterRequest(
    val uuid: String,
    val name: String,
    val voiceId: Long,
    val persona: String
)