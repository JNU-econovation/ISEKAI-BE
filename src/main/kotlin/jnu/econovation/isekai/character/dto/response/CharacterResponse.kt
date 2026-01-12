package jnu.econovation.isekai.character.dto.response

import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO

@ConsistentCopyVisibility
data class CharacterResponse private constructor(
    val id: Long?,
    val author: AuthorInfo,
    val live2dModelUrl: String,
    val backgroundUrl: String,
    val thumbnailUrl: String,
    val name: String,
    val persona: String,
    val voiceId: Long,
    val isPublic: Boolean,
    val isAuthorMe: Boolean
) {
    companion object {
        fun from(viewerId: Long?, characterDTO: CharacterDTO): CharacterResponse {
            return CharacterResponse(
                id = characterDTO.id,
                author = AuthorInfo.fromMemberInfo(characterDTO.author),
                live2dModelUrl = characterDTO.live2dModelUrl,
                backgroundUrl = characterDTO.backgroundUrl,
                thumbnailUrl = characterDTO.thumbnailUrl,
                name = characterDTO.name.value,
                persona = characterDTO.persona.value,
                voiceId = characterDTO.voiceId,
                isPublic = characterDTO.isPublic,
                isAuthorMe = characterDTO.author.id == viewerId
            )
        }
    }
}

@ConsistentCopyVisibility
data class AuthorInfo private constructor(val email: String) {
    companion object {
        fun fromMemberInfo(memberInfo: MemberInfoDTO): AuthorInfo {
            return AuthorInfo(memberInfo.email.value)
        }
    }
}
