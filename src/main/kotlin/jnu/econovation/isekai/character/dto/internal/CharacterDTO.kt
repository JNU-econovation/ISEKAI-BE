package jnu.econovation.isekai.character.dto.internal

import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.model.vo.CharacterName
import jnu.econovation.isekai.character.model.vo.Persona
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO

data class CharacterDTO(
    val id: Long?,
    val author: MemberInfoDTO,
    val live2dModelUrl: String,
    val backgroundUrl: String,
    val thumbnailUrl: String,
    val name: CharacterName,
    val persona: Persona,
    val voiceId: Long,
    val isPublic: Boolean
) {
    companion object {
        fun from(character: Character): CharacterDTO {
            return CharacterDTO(
                id = character.id,
                author = MemberInfoDTO.from(character.author),
                live2dModelUrl = character.live2dModelUrl,
                backgroundUrl = character.backgroundUrl,
                thumbnailUrl = character.thumbnailUrl,
                name = character.name,
                persona = character.persona,
                voiceId = character.voiceId,
                isPublic = character.isPublic
            )
        }
    }
}
