package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.domain.notification.application.port.`in`.DeleteDeviceTokensUseCase
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.domain.user.domain.AuthTokens
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

    @Transactional
    fun refreshIfValid(refreshToken: String): AuthTokens? {
        val refreshTokenRecord = refreshTokenRepository.findByToken(refreshToken) ?: return null

        if (refreshTokenRecord.expiresAt.isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByToken(refreshToken)
            return null
        }

        val user =
            userRepository.findById(refreshTokenRecord.userId)
                ?: throw BusinessException.UserNotFoundException(
                    errors = errorDetailsOf(ErrorField.USER_ID to refreshTokenRecord.userId),
                )

        return rotateToken(user, refreshToken)
    }

    private fun rotateToken(
        user: User,
        oldToken: String,
    ): AuthTokens {
        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val refreshToken = jwtTokenProvider.generateRefreshToken()

        val expiresAt =
            LocalDateTime.now().plusSeconds(
                jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000,
            )

        refreshTokenRepository.rotate(
            oldToken = oldToken,
            newToken = refreshToken,
            expiresAt = expiresAt,
        )

        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
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
}
