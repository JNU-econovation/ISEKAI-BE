package jnu.econovation.isekai.prompt.controller

import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.prompt.dto.response.PromptResponse
import jnu.econovation.isekai.prompt.service.PromptService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/prompts")
class PromptController(private val service: PromptService) {

    @GetMapping
    fun adminPromptPage(@ResolvePageable(allowed = [SortField.CREATED_AT]) pageable: Pageable): Page<PromptResponse> {
        return service.get(pageable)
    }

}