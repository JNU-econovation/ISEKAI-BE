package jnu.econovation.isekai.common.converter

import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.common.dto.request.CustomPageRequest
import jnu.econovation.isekai.common.exception.client.InvalidPageableFieldException
import org.springframework.core.convert.converter.Converter
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import kotlin.collections.find

class CustomPageRequestToPageableConverter(
    private val allowedFields: List<SortField>
) : Converter<CustomPageRequest, Pageable> {

    override fun convert(request: CustomPageRequest): Pageable {
        val page = request.page?.minus(1) ?: 0

        if (page < 0) throw InvalidPageableFieldException("page", "page는 1보다 작을 수 없습니다.")

        val size = request.size ?: 10

        if (size !in 0..100) throw InvalidPageableFieldException("size", "size는 0 ~ 100 사이여야 합니다.")

        val directionString = request.direction ?: "asc"
        val requestFieldString = request.sort ?: SortField.CREATED_AT.requestField

        val direction: Direction = try {
            Direction.fromString(directionString.uppercase())
        } catch (_: IllegalArgumentException) {
            throw InvalidPageableFieldException("direction", directionString)
        }

        val sortField: SortField = allowedFields.find { it.requestField == requestFieldString }
            ?: throw InvalidPageableFieldException("sort", requestFieldString)
        val order = Sort.Order(direction, sortField.dbField)
        return PageRequest.of(page, size, Sort.by(order))
    }
}