package jnu.econovation.isekai.character.controller

import jnu.econovation.isekai.character.dto.request.ConfirmCharacterRequest
import jnu.econovation.isekai.character.dto.request.GenerateBackgroundImageRequest
import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.character.dto.response.CharacterResponse
import jnu.econovation.isekai.character.dto.response.GenerateBackgroundImageResponse
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.service.CharacterCoordinateService
import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/characters")
class CharacterController(
    private val service: CharacterCoordinateService
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
    ): ResponseEntity<CommonResponse<Unit>> {
        val id = service.confirmCharacter(userDetails.memberInfo, request)

        return ResponseEntity
            .created(URI.create("/characters/${id}"))
            .body(CommonResponse.ofSuccess())
    }

    @GetMapping
    fun getCharacterList(
        @ResolvePageable(allowed = [SortField.CREATED_AT]) pageable: Pageable,
        @AuthenticationPrincipal userDetails: IsekAIUserDetails
    ): Page<CharacterResponse> {
        return service.getCharacterList(userDetails.memberInfo, pageable)
    }

    @GetMapping("/{id}")
    fun getCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @PathVariable id: Long
    ): CharacterResponse {
        return service.getCharacterForResponse(userDetails.memberInfo, id)
    }

    @DeleteMapping("/{id}")
    fun deleteCharacter(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @PathVariable id: Long
    ): CommonResponse<Unit> {
        service.delete(userDetails.memberInfo, id)

        return CommonResponse.ofSuccess()
    }

}