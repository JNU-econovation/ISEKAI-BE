package jnu.econovation.isekai.prompt.controller

import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.prompt.dto.internal.PromptRequest
import jnu.econovation.isekai.prompt.dto.response.PromptResponse
import jnu.econovation.isekai.prompt.dto.response.PromptsPageResponse
import jnu.econovation.isekai.prompt.service.PromptService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/admin/prompts")
class PromptController(private val service: PromptService) {

    @PostMapping
    fun savePrompt(@RequestBody prompt: PromptRequest): ResponseEntity<Void> {
        val location = URI.create("/admin/prompts/${service.save(prompt)}")
        return ResponseEntity.created(location).build()
    }

    @GetMapping
    fun getPrompts(@ResolvePageable(allowed = [SortField.CREATED_AT]) pageable: Pageable): Page<PromptsPageResponse> {
        return service.get(pageable)
    }

    @GetMapping("/{id}")
    fun getPrompt(@PathVariable id: Long): PromptResponse = service.getForResponse(id)

    @PutMapping("/{id}")
    fun updatePrompt(
        @PathVariable id: Long,
        @RequestBody request: PromptRequest
    ): ResponseEntity<Void> {
        service.update(id, request)
        return ResponseEntity.ok().build()
    }

}