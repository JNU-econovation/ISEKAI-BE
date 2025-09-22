package jnu.econovation.isekai.gemini.service.internal

import jnu.econovation.isekai.gemini.repository.ShortTermMemoryRepository
import org.springframework.stereotype.Service

@Service
class ShortTermMemoryDataService(
    private val repository: ShortTermMemoryRepository
) {
}