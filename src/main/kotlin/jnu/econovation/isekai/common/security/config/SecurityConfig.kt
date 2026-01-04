package jnu.econovation.isekai.common.security.config

import jnu.econovation.isekai.common.security.filter.JwtAuthFilter
import jnu.econovation.isekai.common.security.filter.RedirectParamFilter
import jnu.econovation.isekai.common.security.oauth.handler.OAuth2SuccessHandler
import jnu.econovation.isekai.common.security.oauth.service.IsekAIOAuth2UserService
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.ForwardedHeaderFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val uriSecurityConfig: UriSecurityConfig,
    private val oAuth2UserService: IsekAIOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val jwtAuthFilter: JwtAuthFilter,
    private val redirectParamFilter: RedirectParamFilter
) {
    companion object {
        private const val CORS_MAX_AGE = 3600L
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = uriSecurityConfig.allowedFrontEndOrigins
        configuration.allowedMethods =
            mutableListOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = mutableListOf("*")
        configuration.allowCredentials = true
        configuration.maxAge = CORS_MAX_AGE

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun forwardedHeaderFilter(): FilterRegistrationBean<ForwardedHeaderFilter> {
        val filter = ForwardedHeaderFilter()
        val registration = FilterRegistrationBean(filter)
        registration.order = Ordered.HIGHEST_PRECEDENCE
        return registration
    }

    @Bean
    fun csrfChannelInterceptor() = object : ChannelInterceptor {
        override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> = message
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http.headers { it.frameOptions { it.sameOrigin() } }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/h2-console/**",
                    "/auth/success",
                    "/error",
                    "/favicon.ico",
                    "/websocket/**"
                )
                    .permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .oauth2Login {
                it.userInfoEndpoint { it -> it.userService(oAuth2UserService) }
                    .successHandler(oAuth2SuccessHandler)
            }
            .addFilterBefore(redirectParamFilter, OAuth2AuthorizationRequestRedirectFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .build()
    }

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

}