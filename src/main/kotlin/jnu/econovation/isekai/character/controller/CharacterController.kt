package jnu.econovation.isekai.character.controller

import jnu.econovation.isekai.character.dto.request.GenerateBackgroundImageRequest
import jnu.econovation.isekai.character.dto.request.ConfirmCharacterRequest
import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.character.dto.response.GenerateBackgroundImageResponse
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

    @PostMapping("/live2d")
    fun generateCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @RequestBody request: GenerateCharacterRequest
    ): GenerateCharacterResponse {
        return service.generateCharacter(userDetails.memberInfo, request)
    }

    @PostMapping("/background-image")
    fun generateBackgroundImage(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @RequestBody request: GenerateBackgroundImageRequest
    ): GenerateBackgroundImageResponse {
        return service.generateBackgroundImage(userDetails.memberInfo, request)
    }

    @PostMapping("/confirm")
    fun confirmCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @RequestBody request: ConfirmCharacterRequest
    ): ResponseEntity<Long> {
        val id = service.confirmCharacter(userDetails.memberInfo, request)

        return ResponseEntity.created(URI("/characters${id}")).build()
    }

}