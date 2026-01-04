package jnu.econovation.isekai.character.controller

import jnu.econovation.isekai.character.dto.request.CharacterConfirmRequest
import jnu.econovation.isekai.character.dto.request.CharacterGenerateRequest
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.service.CharacterService
import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/characters")
class CharacterController(
    private val service: CharacterService
) {

    @PostMapping
    fun generateCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @RequestBody request: CharacterGenerateRequest
    ): GenerateCharacterResponse {
        return service.generateCharacter(userDetails.memberInfo, request)
    }

    @PostMapping("/confirm")
    fun confirmCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @RequestBody request: CharacterConfirmRequest
    ): ResponseEntity<Long> {
        val id = service.confirmCharacter(userDetails.memberInfo, request)

        return ResponseEntity.created(URI("/characters${id}")).build()
    }

}