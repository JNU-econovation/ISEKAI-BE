package jnu.econovation.isekai.rtzr.cache.repository

import jnu.econovation.isekai.rtzr.cache.model.entity.RtzrAccessToken
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface RtzrAccessTokenRepository : CrudRepository<RtzrAccessToken, String> {
    override fun findById(id: String) : Optional<RtzrAccessToken>
}