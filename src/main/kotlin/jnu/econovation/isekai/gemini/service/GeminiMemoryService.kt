package jnu.econovation.isekai.gemini.service

import jnu.econovation.isekai.gemini.service.internal.LongTermMemoryDataService
import jnu.econovation.isekai.gemini.service.internal.ShortTermMemoryDataService
import org.springframework.stereotype.Service

@Service
class GeminiMemoryService(
    private val longTermMemoryDataService: LongTermMemoryDataService,
    private val shortTermMemoryDataService: ShortTermMemoryDataService
) {


}