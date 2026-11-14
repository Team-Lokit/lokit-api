package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.concurrency.LockManager
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.couple.application.port.`in`.CreateCoupleUseCase
import kr.co.lokit.api.domain.user.application.port.OAuthClientPort
import kr.co.lokit.api.domain.user.application.port.OAuthClientRegistryPort
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.domain.user.domain.User
import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class AppleLoginServiceTest {
    private val oAuthClientRegistry = mock<OAuthClientRegistryPort>()
    private val userRepository = mock<UserRepositoryPort>()
    private val refreshTokenRepository = mock<RefreshTokenRepositoryPort>()
    private val jwtTokenProvider = mock<JwtTokenProvider>()
    private val createCoupleUseCase = mock<CreateCoupleUseCase>()

    private val service =
        AppleLoginService(
            oAuthClientRegistry,
            userRepository,
            refreshTokenRepository,
            jwtTokenProvider,
            createCoupleUseCase,
            LockManager(),
        )

    @BeforeEach
    fun setUp() {
        val client = mock<OAuthClientPort>()
        val userInfo = mock<OAuthUserInfo>()
        whenever(oAuthClientRegistry.getClient(OAuthProvider.APPLE)).thenReturn(client)
        whenever(client.getAccessToken("auth-code")).thenReturn("id-token")
        whenever(client.getUserInfo("id-token")).thenReturn(userInfo)
        whenever(userInfo.email).thenReturn("apple@test.com")
        whenever(createCoupleUseCase.createIfNone(any(), any())).thenReturn(createCouple(id = 1L, userIds = listOf(1L)))
        whenever(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token")
        whenever(jwtTokenProvider.generateRefreshToken()).thenReturn("refresh-token")
        whenever(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(604800000L)
        whenever(userRepository.save(any())).thenAnswer { (it.arguments[0] as User).copy(id = 1L) }
    }

    private fun savedUser(): User = argumentCaptor<User>().apply { verify(userRepository).save(capture()) }.firstValue

    @Test
    fun `첫 로그인이면 애플이 준 이름을 닉네임으로 저장한다`() {
        val result = service.login("auth-code", "김승진")

        assertEquals("김승진", savedUser().name)
        assertEquals(true, result.isNewUser)
    }

    @Test
    fun `첫 로그인인데 이름을 받지 못하면 이메일 기반 닉네임으로 저장한다`() {
        service.login("auth-code")

        assertEquals("apple", savedUser().name)
    }

    @Test
    fun `기존 사용자는 이름을 받아도 닉네임을 바꾸지 않는다`() {
        whenever(userRepository.findByEmail("apple@test.com")).thenReturn(User(id = 1L, email = "apple@test.com", name = "기존닉네임"))

        service.login("auth-code", "김승진")

        verify(userRepository, never()).save(any())
    }
}
