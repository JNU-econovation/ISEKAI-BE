package jnu.econovation.isekai.storage.controller

import jakarta.validation.Valid
import jnu.econovation.isekai.common.dto.response.CommonResponse
import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import jnu.econovation.isekai.storage.dto.request.PresignRequest
import jnu.econovation.isekai.storage.dto.response.PresignResponse
import jnu.econovation.isekai.storage.service.PresignService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/upload/presigned-url")
class PresignController(
    private val service: PresignService
) {

    @GetMapping
    fun presign(
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @Valid request: PresignRequest
    ): CommonResponse<PresignResponse> {
        val url: String = service.generatePresignedPutUrl(request, userDetails.memberInfo)

        return CommonResponse.ofSuccess(PresignResponse(url))
    }
}