package kr.co.lokit.api.domain.user.presentation

import jakarta.servlet.http.Cookie
import kr.co.lokit.api.common.constants.DomainCookie
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.security.LoginAccessDeniedHandler
import kr.co.lokit.api.config.security.LoginAuthenticationEntryPoint
import kr.co.lokit.api.config.security.SecurityConfig
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.couple.application.CoupleCookieStatusResolver
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.domain.user.application.OAuthLoginServiceRegistry
import kr.co.lokit.api.domain.user.infrastructure.oauth.apple.AppleOAuthProperties
import kr.co.lokit.api.domain.user.infrastructure.oauth.kakao.KakaoOAuthProperties
import kr.co.lokit.api.fixture.userAuth
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseCookie
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 실제 SecurityConfig 를 로드해 유효하지 않은 토큰으로 로그아웃해도 401 대신 쿠키가 삭제되는지 검증한다.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, LoginAuthenticationEntryPoint::class, LoginAccessDeniedHandler::class)
@TestPropertySource(properties = ["cors.allowed-origins=http://localhost:3000"])
class AuthControllerLogoutSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var compositeAuthenticationResolver: CompositeAuthenticationResolver

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @MockitoBean
    lateinit var cookieProperties: CookieProperties

    @MockitoBean
    lateinit var cookieGenerator: CookieGenerator

    @MockitoBean
    lateinit var loginServiceRegistry: OAuthLoginServiceRegistry

    @MockitoBean
    lateinit var coupleCookieStatusResolver: CoupleCookieStatusResolver

    @MockitoBean
    lateinit var appleOAuthProperties: AppleOAuthProperties

    @MockitoBean
    lateinit var kakaoOAuthProperties: KakaoOAuthProperties

    @BeforeEach
    fun stubCookies() {
        whenever(cookieGenerator.resolveCookieName(DomainCookie.ACCESS_TOKEN)).thenReturn(DomainCookie.ACCESS_TOKEN.value)
        whenever(cookieGenerator.resolveCookieName(DomainCookie.REFRESH_TOKEN)).thenReturn(DomainCookie.REFRESH_TOKEN.value)
        whenever(cookieGenerator.clearAccessTokenCookie(any()))
            .thenReturn(ResponseCookie.from("accessToken", "").maxAge(0).build())
        whenever(cookieGenerator.clearRefreshTokenCookie(any()))
            .thenReturn(ResponseCookie.from("refreshToken", "").maxAge(0).build())
        whenever(cookieGenerator.clearCoupleStatusCookie(any()))
            .thenReturn(ResponseCookie.from("coupleStatus", "").maxAge(0).build())
    }

    @Test
    fun `유효하지 않은 토큰으로 로그아웃하면 서비스 호출 없이 쿠키를 삭제하고 204를 반환한다`() {
        whenever(compositeAuthenticationResolver.authenticate(any())).thenReturn(null)
        whenever(authService.refreshIfValid(any())).thenReturn(null)

        mockMvc
            .perform(
                post("/auth/logout")
                    .cookie(
                        Cookie(DomainCookie.ACCESS_TOKEN.value, "invalid-access"),
                        Cookie(DomainCookie.REFRESH_TOKEN.value, "invalid-refresh"),
                    ),
            ).andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("accessToken", 0))
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(cookie().maxAge("coupleStatus", 0))

        verify(authService, never()).logout(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `토큰 없이 로그아웃해도 쿠키를 삭제하고 204를 반환한다`() {
        mockMvc
            .perform(post("/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("accessToken", 0))
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(cookie().maxAge("coupleStatus", 0))

        verify(authService, never()).logout(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `유효한 인증으로 로그아웃하면 서비스를 호출하고 쿠키를 삭제한다`() {
        mockMvc
            .perform(
                post("/auth/logout")
                    .with(authentication(userAuth()))
                    .cookie(Cookie(DomainCookie.REFRESH_TOKEN.value, "refresh-this-device")),
            )
            .andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("accessToken", 0))
            .andExpect(cookie().maxAge("refreshToken", 0))
            .andExpect(cookie().maxAge("coupleStatus", 0))

        verify(authService).logout(1L, "refresh-this-device", null)
    }

}
