package jnu.econovation.isekai.persona.controller

import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.persona.dto.internal.PersonaRequest
import jnu.econovation.isekai.persona.dto.response.PersonaResponse
import jnu.econovation.isekai.persona.dto.response.PersonaPageResponse
import jnu.econovation.isekai.persona.service.PersonaService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/admin/prompts")
class PersonaController(private val service: PersonaService) {

    @PostMapping
    fun savePrompt(@RequestBody prompt: PersonaRequest): ResponseEntity<Void> {
        val location = URI.create("/admin/prompts/${service.save(prompt)}")
        return ResponseEntity.created(location).build()
    }

    @GetMapping
    fun getPrompts(@ResolvePageable(allowed = [SortField.CREATED_AT]) pageable: Pageable): Page<PersonaPageResponse> {
        return service.get(pageable)
    }

    @GetMapping("/{id}")
    fun getPrompt(@PathVariable id: Long): PersonaResponse = service.getForResponse(id)

    @PutMapping("/{id}")
    fun updatePrompt(
        @PathVariable id: Long,
        @RequestBody request: PersonaRequest
    ): ResponseEntity<Void> {
        service.update(id, request)
        return ResponseEntity.ok().build()
    }

}