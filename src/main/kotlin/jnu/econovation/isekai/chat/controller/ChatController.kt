package jnu.econovation.isekai.chat.controller

import jnu.econovation.isekai.chat.dto.response.ChatRestResponse
import jnu.econovation.isekai.chat.service.ChatRestService
import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.common.security.dto.internal.IsekAIUserDetails
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/characters/{characterId}/chats")
class ChatController(
    private val service: ChatRestService
) {

    @GetMapping
    fun getChats(
        @PathVariable characterId: Long,
        @AuthenticationPrincipal userDetails: IsekAIUserDetails,
        @ResolvePageable(allowed = [SortField.CREATED_AT]) pageable: Pageable
    ): Page<ChatRestResponse> {
        return service.getChats(
            memberInfoDTO = userDetails.memberInfo,
            characterId = characterId,
            pageable = pageable
        )
    }
}