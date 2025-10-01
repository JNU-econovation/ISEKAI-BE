package jnu.econovation.isekai.common.resolver

import jnu.econovation.isekai.common.annotaion.ResolvePageable
import jnu.econovation.isekai.common.constant.SortField
import jnu.econovation.isekai.common.converter.CustomPageRequestToPageableConverter
import jnu.econovation.isekai.common.dto.request.CustomPageRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.data.domain.Pageable
import kotlin.jvm.java

@Component
class CustomPageableArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(ResolvePageable::class.java)
                && parameter.parameterType == Pageable::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val annotation = parameter.getParameterAnnotation(ResolvePageable::class.java)!!
        val allowedFields: List<SortField> = annotation.allowed.toList()

        val sort = webRequest.getParameter("sort")
        val dir = webRequest.getParameter("direction")
        val pageStr = webRequest.getParameter("page")
        val sizeStr = webRequest.getParameter("size")
        val request = CustomPageRequest(
            sort = sort,
            direction = dir,
            size = sizeStr?.toIntOrNull(),
            page = pageStr?.toIntOrNull()
        )

        return CustomPageRequestToPageableConverter(allowedFields).convert(request)
    }
}