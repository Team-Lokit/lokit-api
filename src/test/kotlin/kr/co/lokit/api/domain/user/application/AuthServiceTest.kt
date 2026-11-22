package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.notification.application.port.`in`.DeleteDeviceTokensUseCase
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRecord
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.domain.user.domain.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {
    @Mock
    lateinit var userRepository: UserRepositoryPort

    @Mock
    lateinit var refreshTokenRepository: RefreshTokenRepositoryPort

    @Mock
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    lateinit var deleteDeviceTokensUseCase: DeleteDeviceTokensUseCase

    @InjectMocks
    lateinit var authService: AuthService

    private val testUser = User(id = 1L, email = "test@test.com", name = "테스트")

    @Test
    fun `유효한 리프레시 토큰으로 새 토큰 쌍을 발급한다`() {
        val refreshTokenRecord =
            RefreshTokenRecord(
                userId = 1L,
                expiresAt = LocalDateTime.now().plusDays(7),
            )

        `when`(refreshTokenRepository.findByTokenForUpdate("valid-token")).thenReturn(refreshTokenRecord)
        `when`(userRepository.findById(1L)).thenReturn(testUser)
        `when`(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token")
        `when`(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token")
        `when`(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(604800000L)

        val result = authService.refreshIfValid("valid-token")

        assertNotNull(result)
        assertEquals("new-access-token", result.accessToken)
        assertEquals("new-refresh-token", result.refreshToken)
    }

    @Test
    fun `존재하지 않는 리프레시 토큰이면 null을 반환한다`() {
        `when`(refreshTokenRepository.findByTokenForUpdate("unknown-token")).thenReturn(null)

        val result = authService.refreshIfValid("unknown-token")

        assertNull(result)
    }

    @Test
    fun `만료된 리프레시 토큰이면 삭제 후 null을 반환한다`() {
        val expiredRecord =
            RefreshTokenRecord(
                userId = 1L,
                expiresAt = LocalDateTime.now().minusDays(1),
            )
        `when`(refreshTokenRepository.findByTokenForUpdate("expired-token")).thenReturn(expiredRecord)

        val result = authService.refreshIfValid("expired-token")

        assertNull(result)
        verify(refreshTokenRepository).deleteByToken("expired-token")
    }

    @Test
    fun `토큰 재발급 시 새 리프레시 토큰이 회전 저장된다`() {
        val refreshTokenRecord =
            RefreshTokenRecord(
                userId = 1L,
                expiresAt = LocalDateTime.now().plusDays(7),
            )
        `when`(refreshTokenRepository.findByTokenForUpdate("valid-token")).thenReturn(refreshTokenRecord)
        `when`(userRepository.findById(1L)).thenReturn(testUser)
        `when`(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token")
        `when`(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token")
        `when`(jwtTokenProvider.getRefreshTokenExpirationMillis()).thenReturn(604800000L)

        authService.refreshIfValid("valid-token")

        verify(refreshTokenRepository).markRotated(org.mockito.kotlin.eq("valid-token"), org.mockito.kotlin.any())
        verify(refreshTokenRepository).save(
            org.mockito.kotlin.eq(1L),
            org.mockito.kotlin.eq("new-refresh-token"),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `유예 시간 안에 이미 교체된 토큰이 오면 액세스 토큰만 발급하고 리프레시 토큰은 건드리지 않는다`() {
        val now = LocalDateTime.of(2026, 11, 22, 9, 0, 0)
        val rotatedRecord =
            RefreshTokenRecord(userId = 1L, expiresAt = now.plusDays(30), rotatedAt = now.minusSeconds(5))
        `when`(refreshTokenRepository.findByTokenForUpdate("old-token")).thenReturn(rotatedRecord)
        `when`(userRepository.findById(1L)).thenReturn(testUser)
        `when`(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new-access-token")

        val result = authService.refreshIfValid("old-token", now)

        assertNotNull(result)
        assertEquals("new-access-token", result.accessToken)
        assertNull(result.refreshToken)
        verify(refreshTokenRepository, never()).markRotated(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(refreshTokenRepository, never()).save(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(refreshTokenRepository, never()).deleteByToken(org.mockito.kotlin.any())
    }

    @Test
    fun `유예 시간이 지난 교체된 토큰이 오면 삭제하고 null을 반환한다`() {
        val now = LocalDateTime.of(2026, 11, 22, 9, 0, 0)
        val rotatedRecord =
            RefreshTokenRecord(
                userId = 1L,
                expiresAt = now.plusDays(30),
                rotatedAt = now.minusSeconds(AuthService.ROTATION_GRACE_SECONDS + 1),
            )
        `when`(refreshTokenRepository.findByTokenForUpdate("old-token")).thenReturn(rotatedRecord)

        val result = authService.refreshIfValid("old-token", now)

        assertNull(result)
        verify(refreshTokenRepository).deleteByToken("old-token")
    }

    @Test
    fun `로그아웃하면 이 기기의 리프레시 토큰만 삭제하고 다른 기기의 토큰은 남긴다`() {
        authService.logout(1L, "refresh-this-device")

        verify(refreshTokenRepository).deleteByToken("refresh-this-device")
        verify(refreshTokenRepository, never()).deleteByUserId(org.mockito.kotlin.any<Long>())
    }

    @Test
    fun `리프레시 토큰 없이 로그아웃하면 서버의 리프레시 토큰은 건드리지 않는다`() {
        authService.logout(1L, null)

        verify(refreshTokenRepository, never()).deleteByToken(org.mockito.kotlin.any<String>())
        verify(refreshTokenRepository, never()).deleteByUserId(org.mockito.kotlin.any<Long>())
    }

    @Test
    fun `deviceToken 없이 로그아웃하면 디바이스 토큰이 전부 삭제된다`() {
        authService.logout(1L, "refresh")

        verify(deleteDeviceTokensUseCase).deleteAllByUserId(1L)
    }

    @Test
    fun `deviceToken 을 주고 로그아웃하면 그 기기 토큰만 삭제하고 리프레시 토큰도 삭제한다`() {
        authService.logout(1L, "refresh", "fcm-1")

        verify(deleteDeviceTokensUseCase).deleteByUserIdAndToken(1L, "fcm-1")
        verify(deleteDeviceTokensUseCase, never()).deleteAllByUserId(org.mockito.kotlin.any<Long>())
        verify(refreshTokenRepository).deleteByToken("refresh")
    }

    @Test
    fun `deviceToken 이 공백이면 디바이스 토큰이 전부 삭제된다`() {
        authService.logout(1L, "refresh", " ")

        verify(deleteDeviceTokensUseCase).deleteAllByUserId(1L)
        verify(deleteDeviceTokensUseCase, never()).deleteByUserIdAndToken(org.mockito.kotlin.any<Long>(), org.mockito.kotlin.any<String>())
    }

    @Test
    fun `사용자를 찾을 수 없으면 UserNotFoundException이 발생한다`() {
        val refreshTokenRecord =
            RefreshTokenRecord(
                userId = 1L,
                expiresAt = LocalDateTime.now().plusDays(7),
            )
        `when`(refreshTokenRepository.findByTokenForUpdate("valid-token")).thenReturn(refreshTokenRecord)
        `when`(userRepository.findById(1L)).thenReturn(null)

        assertThrows<BusinessException.UserNotFoundException> {
            authService.refreshIfValid("valid-token")
        }
    }
}
