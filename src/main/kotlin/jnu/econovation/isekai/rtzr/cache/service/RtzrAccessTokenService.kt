package jnu.econovation.isekai.rtzr.cache.service

import jnu.econovation.isekai.rtzr.cache.model.entity.RtzrAccessToken
import jnu.econovation.isekai.rtzr.cache.repository.RtzrAccessTokenRepository
import org.springframework.stereotype.Service
import kotlin.jvm.optionals.getOrNull

@Service
class RtzrAccessTokenService(
    private val repository: RtzrAccessTokenRepository
) {

    companion object {
        private const val ID = "rtzr_access_token"
    }

    fun get() = repository.findById(ID).getOrNull()?.value

    //todo: 동시성 제어
    fun overwrite(token: String) : String {
        val entity = RtzrAccessToken(ID, token)
        return repository.save(entity).value
    }
}