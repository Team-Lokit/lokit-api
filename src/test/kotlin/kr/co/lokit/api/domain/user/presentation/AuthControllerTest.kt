package kr.co.lokit.api.domain.user.presentation

import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.couple.application.CoupleCookieStatusResolver
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.domain.user.application.OAuthLoginServiceRegistry
import kr.co.lokit.api.domain.user.infrastructure.oauth.apple.AppleOAuthProperties
import kr.co.lokit.api.domain.user.infrastructure.oauth.kakao.KakaoOAuthProperties
import kr.co.lokit.api.fixture.userAuth
import kr.co.lokit.api.domain.user.application.LoginService
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.domain.AuthTokens
import kr.co.lokit.api.domain.user.domain.LoginResult
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
class AuthControllerTest {
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
    fun stubClearCookies() {
        // 목 CookieGenerator 가 null 을 돌려주면 컨트롤러의 toString() 에서 NPE 가 나므로 실제 쿠키로 스텁한다.
        whenever(cookieGenerator.clearAccessTokenCookie(any())).thenReturn(ResponseCookie.from("accessToken", "").build())
        whenever(cookieGenerator.clearRefreshTokenCookie(any())).thenReturn(ResponseCookie.from("refreshToken", "").build())
        whenever(cookieGenerator.clearCoupleStatusCookie(any())).thenReturn(ResponseCookie.from("coupleStatus", "").build())
    }

    @Test
    fun `deviceToken 을 본문에 담아 로그아웃하면 그 토큰을 서비스에 넘기고 204를 반환한다`() {
        mockMvc
            .perform(
                post("/auth/logout")
                    .with(authentication(userAuth()))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"deviceToken":"fcm-1"}"""),
            ).andExpect(status().isNoContent)

        verify(authService).logout(1L, "fcm-1")
    }

    @Test
    fun `본문 없이 로그아웃하면 deviceToken 을 null 로 넘기고 204를 반환한다`() {
        mockMvc
            .perform(
                post("/auth/logout")
                    .with(authentication(userAuth()))
                    .with(csrf()),
            ).andExpect(status().isNoContent)

        verify(authService).logout(1L, null)
    }

    private fun stubAppleLogin(): LoginService {
        val service = mock<LoginService>()
        whenever(appleOAuthProperties.frontRedirectUri).thenReturn("http://localhost:3000")
        whenever(loginServiceRegistry.getService(OAuthProvider.APPLE)).thenReturn(service)
        whenever(service.login(any(), anyOrNull()))
            .thenReturn(LoginResult(userId = 1L, tokens = AuthTokens("access", "refresh"), isNewUser = true))
        whenever(cookieGenerator.createAccessTokenCookie(any(), any())).thenReturn(ResponseCookie.from("accessToken", "a").build())
        whenever(cookieGenerator.createRefreshTokenCookie(any(), any())).thenReturn(ResponseCookie.from("refreshToken", "r").build())
        return service
    }

    @Test
    fun `애플 인증은 이름 동의를 요청한다`() {
        mockMvc
            .perform(get("/auth/apple").with(authentication(userAuth())))
            .andExpect(status().isFound)
            .andExpect(header().string("Location", containsString("scope=name%20email")))
    }

    @Test
    fun `애플 콜백의 user 파라미터에서 이름을 꺼내 로그인에 넘긴다`() {
        val service = stubAppleLogin()

        mockMvc
            .perform(
                post("/auth/apple/callback")
                    .with(authentication(userAuth()))
                    .with(csrf())
                    .param("code", "auth-code")
                    .param("user", """{"name":{"firstName":"승진","lastName":"김"},"email":"a@b.com"}"""),
            ).andExpect(status().isFound)

        verify(service).login("auth-code", "김승진")
    }

    @Test
    fun `재로그인이라 user 파라미터가 없으면 이름 없이 로그인한다`() {
        val service = stubAppleLogin()

        mockMvc
            .perform(
                post("/auth/apple/callback")
                    .with(authentication(userAuth()))
                    .with(csrf())
                    .param("code", "auth-code"),
            ).andExpect(status().isFound)

        verify(service).login("auth-code", null)
    }
}
