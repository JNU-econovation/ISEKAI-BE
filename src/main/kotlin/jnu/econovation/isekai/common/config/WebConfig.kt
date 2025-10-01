package jnu.econovation.isekai.common.config

import jnu.econovation.isekai.common.resolver.CustomPageableArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val customPageableArgumentResolver: CustomPageableArgumentResolver
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(customPageableArgumentResolver)
    }
}
