package kr.co.lokit.api.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.co.lokit.api.common.constants.DomainCookie
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.domain.user.application.AuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthenticationFilter(
    private val compositeAuthenticationResolver: CompositeAuthenticationResolver,
    private val authService: AuthService,
    private val cookieGenerator: CookieGenerator,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val accessToken = getTokenFromCookie(request, DomainCookie.ACCESS_TOKEN)
            val refreshToken = getTokenFromCookie(request, DomainCookie.REFRESH_TOKEN)

            log.debug(
                "Auth filter: uri={}, hasAccessToken={}, hasRefreshToken={}, cookieNames={}",
                request.requestURI,
                accessToken != null,
                refreshToken != null,
                request.cookies?.map { it.name }?.joinToString(", ") ?: "none",
            )

            if (SecurityContextHolder.getContext().authentication != null) {
                filterChain.doFilter(request, response)
                return
            }

            if (accessToken != null) {
                val authentication = compositeAuthenticationResolver.authenticate(accessToken)
                if (authentication != null) {
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    val securityContext = SecurityContextHolder.createEmptyContext()
                    securityContext.authentication = authentication
                    SecurityContextHolder.setContext(securityContext)
                    log.debug("Authentication successful for user: ${authentication.name}")
                    filterChain.doFilter(request, response)
                    return
                } else {
                    log.debug("AccessToken validation failed")
                }
            }

            if (refreshToken != null) {
                val tokens = authService.refreshIfValid(refreshToken)
                if (tokens != null) {
                    setTokenCookies(request, response, tokens.accessToken, tokens.refreshToken)
                    // 이 요청 안에서 쿠키의 리프레시 토큰은 이미 교체됐다. 로그아웃 등은 새 토큰을 기준으로 처리해야 한다.
                    request.setAttribute(ROTATED_REFRESH_TOKEN_ATTRIBUTE, tokens.refreshToken)

                    val authentication = compositeAuthenticationResolver.authenticate(tokens.accessToken)
                    if (authentication != null) {
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        val securityContext = SecurityContextHolder.createEmptyContext()
                        securityContext.authentication = authentication
                        SecurityContextHolder.setContext(securityContext)
                        log.debug("Token refreshed and authentication successful for user: ${authentication.name}")
                    }
                } else {
                    log.debug("RefreshToken is invalid or expired")
                }
            } else {
                log.debug("No tokens found in cookies")
            }
        } catch (e: Exception) {
            log.error("Cannot set user authentication: ${e.message}", e)
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val ROTATED_REFRESH_TOKEN_ATTRIBUTE = "lokit.rotatedRefreshToken"
    }

    private fun getTokenFromCookie(
        request: HttpServletRequest,
        name: DomainCookie,
    ): String? =
        request.cookies
            ?.find { it.name == cookieGenerator.resolveCookieName(name) }
            ?.value
            ?.takeIf { it.isNotBlank() && !it.contains(" ") }

    private fun setTokenCookies(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessToken: String,
        refreshToken: String,
    ) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieGenerator.createAccessTokenCookie(request, accessToken).toString(),
        )
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieGenerator.createRefreshTokenCookie(request, refreshToken).toString(),
        )
    }
}
