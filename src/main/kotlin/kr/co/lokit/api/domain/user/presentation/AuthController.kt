package kr.co.lokit.api.domain.user.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorCode
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.domain.couple.application.CoupleCookieStatusResolver
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.domain.user.application.OAuthLoginServiceRegistry
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.infrastructure.oauth.apple.AppleOAuthProperties
import kr.co.lokit.api.domain.user.infrastructure.oauth.kakao.KakaoOAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("auth")
class AuthController(
    private val loginServiceRegistry: OAuthLoginServiceRegistry,
    private val authService: AuthService,
    private val coupleCookieStatusResolver: CoupleCookieStatusResolver,
    private val appleOAuthProperties: AppleOAuthProperties,
    private val kakaoOAuthProperties: KakaoOAuthProperties,
    private val cookieGenerator: CookieGenerator,
    @Value("\${redirect.local-host}") private val localHostRedirect: String,
    @Value("\${redirect.allowed-domain}") private val allowedDomain: String,
) : AuthApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @ResponseStatus(HttpStatus.FOUND)
    @GetMapping("kakao")
    override fun kakaoAuthorize(
        @RequestParam(required = false) redirect: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Unit> {
        val resolvedRedirect = redirect ?: resolveRedirectFromReferer(req)
        val state =
            resolvedRedirect
                ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
                .orEmpty()

        val authUrl =
            KakaoOAuthProperties.AUTHORIZATION_URL +
                "?client_id=${kakaoOAuthProperties.clientId}" +
                "&redirect_uri=${kakaoOAuthProperties.redirectUri}" +
                "&response_type=code" +
                "&state=$state"

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(authUrl))
            .build()
    }

    @ResponseStatus(HttpStatus.FOUND)
    @GetMapping("apple")
    override fun appleAuthorize(
        @RequestParam(required = false) redirect: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Unit> {
        val resolvedRedirect = redirect ?: resolveRedirectFromReferer(req)
        val state =
            resolvedRedirect
                ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
                .orEmpty()

        val authUrl =
            AppleOAuthProperties.AUTHORIZATION_URL +
                "?client_id=${appleOAuthProperties.clientId}" +
                "&redirect_uri=${appleOAuthProperties.redirectUri}" +
                "&response_type=code" +
                "&scope=email" +
                "&response_mode=form_post" +
                "&state=$state"

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(authUrl))
            .build()
    }

    @ResponseStatus(HttpStatus.FOUND)
    @GetMapping("kakao/callback")
    override fun kakaoCallback(
        @RequestParam code: String,
        @RequestParam(required = false) state: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Unit> {
        val redirectUri =
            state
                ?.takeIf { it.isNotBlank() }
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
                ?.takeIf { isAllowedRedirect(it) }
                ?: kakaoOAuthProperties.frontRedirectUri

        return try {
            val service = loginServiceRegistry.getService(OAuthProvider.KAKAO)
            val loginResult = service.login(code)
            val accessTokenCookie = cookieGenerator.createAccessTokenCookie(req, loginResult.tokens.accessToken)
            val refreshTokenCookie = cookieGenerator.createRefreshTokenCookie(req, loginResult.tokens.refreshToken)
            log.info(
                "Kakao callback success: redirectUri={}, issuedCookies={}",
                redirectUri,
                listOf("accessToken", "refreshToken").joinToString(","),
            )

            ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .location(URI.create(redirectUri))
                .build()
        } catch (ex: BusinessException) {
            log.info("Kakao callback failed: code={}, redirectUri={}", ex.errorCode.code, redirectUri)
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(buildErrorRedirectUri(redirectUri, ex.errorCode.code)))
                .build()
        } catch (ex: Exception) {
            log.error("Kakao callback unexpected error: redirectUri={}", redirectUri, ex)
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(buildErrorRedirectUri(redirectUri, ErrorCode.INTERNAL_SERVER_ERROR.code)))
                .build()
        }
    }

    @ResponseStatus(HttpStatus.FOUND)
    @PostMapping("apple/callback")
    override fun appleCallback(
        @RequestParam code: String,
        @RequestParam(required = false) state: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Unit> {
        val redirectUri =
            state
                ?.takeIf { it.isNotBlank() }
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
                ?.takeIf { isAllowedRedirect(it) }
                ?: appleOAuthProperties.frontRedirectUri

        return try {
            val service = loginServiceRegistry.getService(OAuthProvider.APPLE)
            val loginResult = service.login(code)
            val accessTokenCookie = cookieGenerator.createAccessTokenCookie(req, loginResult.tokens.accessToken)
            val refreshTokenCookie = cookieGenerator.createRefreshTokenCookie(req, loginResult.tokens.refreshToken)
            log.info(
                "Apple callback success: redirectUri={}, issuedCookies={}",
                redirectUri,
                listOf("accessToken", "refreshToken").joinToString(","),
            )

            ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .location(URI.create(redirectUri))
                .build()
        } catch (ex: BusinessException) {
            log.info("Apple callback failed: code={}, redirectUri={}", ex.errorCode.code, redirectUri)
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(buildErrorRedirectUri(redirectUri, ex.errorCode.code)))
                .build()
        } catch (ex: Exception) {
            log.error("Apple callback unexpected error: redirectUri={}", redirectUri, ex)
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(buildErrorRedirectUri(redirectUri, ErrorCode.INTERNAL_SERVER_ERROR.code)))
                .build()
        }
    }

    @PostMapping("logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun logout(
        @CurrentUserId userId: Long,
        req: HttpServletRequest,
        res: HttpServletResponse,
    ) {
        authService.logout(userId)
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
        res.setHeader("Pragma", "no-cache")
        res.setDateHeader("Expires", 0)
        res.addHeader(HttpHeaders.SET_COOKIE, cookieGenerator.clearAccessTokenCookie(req).toString())
        res.addHeader(HttpHeaders.SET_COOKIE, cookieGenerator.clearRefreshTokenCookie(req).toString())
        res.addHeader(HttpHeaders.SET_COOKIE, cookieGenerator.clearCoupleStatusCookie(req).toString())
    }

    private fun resolveRedirectFromReferer(req: HttpServletRequest): String? {
        val referer = req.getHeader("Referer") ?: return null
        val uri = URI.create(referer)
        if (uri.host == localHostRedirect) {
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return "${uri.scheme}://${uri.host}$port"
        }
        return null
    }

    private fun isAllowedRedirect(uri: String): Boolean =
        try {
            URI.create(uri).host?.endsWith(allowedDomain) == true
        } catch (_: Exception) {
            false
        }

    private fun buildErrorRedirectUri(
        redirectUri: String,
        errorCode: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(redirectUri)
            .queryParam("oauth_error", errorCode)
            .build(true)
            .toUriString()
}
