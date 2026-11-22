package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.notification.application.port.`in`.DeleteDeviceTokensUseCase
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.domain.user.domain.AuthTokens
import kr.co.lokit.api.domain.user.domain.RefreshedTokens
import kr.co.lokit.api.domain.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuthService(
    private val userRepository: UserRepositoryPort,
    private val refreshTokenRepository: RefreshTokenRepositoryPort,
    private val jwtTokenProvider: JwtTokenProvider,
    private val deleteDeviceTokensUseCase: DeleteDeviceTokensUseCase,
) {
    /**
     * 이 기기의 리프레시 토큰만 폐기한다. 여러 기기 로그인을 허용하므로 다른 기기의 세션은 유지된다.
     * (전체 폐기는 탈퇴 시 UserWithdrawService 가 담당)
     */
    @Transactional
    fun logout(
        userId: Long,
        refreshToken: String?,
        deviceToken: String? = null,
    ) {
        if (!refreshToken.isNullOrBlank()) {
            refreshTokenRepository.deleteByToken(refreshToken)
        }
        if (deviceToken.isNullOrBlank()) {
            deleteDeviceTokensUseCase.deleteAllByUserId(userId)
        } else {
            deleteDeviceTokensUseCase.deleteByUserIdAndToken(userId, deviceToken)
        }
    }

    /**
     * 리프레시 토큰으로 토큰을 갱신한다.
     *
     * 앱을 열 때처럼 같은 리프레시 토큰을 실은 요청이 동시에 여러 개 들어올 수 있다.
     * - 토큰 행을 FOR UPDATE 로 잠가 갱신을 직렬화한다. 첫 요청만 새 리프레시 토큰을 발급한다.
     * - 교체된 옛 토큰은 바로 지우지 않고 rotatedAt 을 기록해 [ROTATION_GRACE_SECONDS] 동안
     *   액세스 토큰만 발급해 준다(refreshToken = null). 뒤늦은 요청이 401 을 받거나 쿠키를 덮어쓰지 않게 하기 위함.
     * - 유예 시간이 지난 옛 토큰은 무효로 보고 삭제한다.
     */
    @Transactional
    fun refreshIfValid(
        refreshToken: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): RefreshedTokens? {
        val refreshTokenRecord = refreshTokenRepository.findByTokenForUpdate(refreshToken) ?: return null

        if (refreshTokenRecord.expiresAt.isBefore(now)) {
            refreshTokenRepository.deleteByToken(refreshToken)
            return null
        }

        val rotatedAt = refreshTokenRecord.rotatedAt
        if (rotatedAt != null && rotatedAt.plusSeconds(ROTATION_GRACE_SECONDS).isBefore(now)) {
            refreshTokenRepository.deleteByToken(refreshToken)
            return null
        }

        val user =
            userRepository.findById(refreshTokenRecord.userId)
                ?: throw BusinessException.UserNotFoundException(
                    errors = errorDetailsOf(ErrorField.USER_ID to refreshTokenRecord.userId),
                )

        if (rotatedAt != null) {
            return RefreshedTokens(accessToken = jwtTokenProvider.generateAccessToken(user), refreshToken = null)
        }

        return rotateToken(user, refreshToken, now)
    }

    private fun rotateToken(
        user: User,
        oldToken: String,
        now: LocalDateTime,
    ): RefreshedTokens {
        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken()

        refreshTokenRepository.markRotated(oldToken, now)
        refreshTokenRepository.save(
            userId = user.id,
            token = newRefreshToken,
            expiresAt = now.plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000),
        )

        return RefreshedTokens(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
        )
    }

    fun generateTokensAndSave(user: User): AuthTokens {
        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val refreshToken = jwtTokenProvider.generateRefreshToken()

        val expiresAt =
            LocalDateTime.now().plusSeconds(
                jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000,
            )

        refreshTokenRepository.save(
            userId = user.id,
            token = refreshToken,
            expiresAt = expiresAt,
        )

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }
    companion object {
        /** 교체된 옛 리프레시 토큰을 봐주는 시간. 앱 시작 시 동시에 나가는 요청들을 덮는 정도면 충분하다. */
        const val ROTATION_GRACE_SECONDS = 30L
    }
}
