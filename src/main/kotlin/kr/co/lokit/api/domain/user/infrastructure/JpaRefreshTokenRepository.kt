package kr.co.lokit.api.domain.user.infrastructure

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.config.security.RefreshTokenHasher
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRecord
import kr.co.lokit.api.domain.user.application.port.RefreshTokenRepositoryPort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JpaRefreshTokenRepository(
    private val refreshTokenJpaRepository: RefreshTokenJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val refreshTokenHasher: RefreshTokenHasher,
) : RefreshTokenRepositoryPort {
    override fun findByTokenForUpdate(token: String): RefreshTokenRecord? =
        refreshTokenJpaRepository.findByTokenHashForUpdate(refreshTokenHasher.hash(token))?.let {
            RefreshTokenRecord(
                userId = it.user.nonNullId(),
                expiresAt = it.expiresAt,
                rotatedAt = it.rotatedAt,
            )
        }

    override fun save(
        userId: Long,
        token: String,
        expiresAt: LocalDateTime,
    ) {
        val userEntity =
            userJpaRepository.findByIdOrNull(userId)
                ?: throw BusinessException.UserNotFoundException(
                    errors = errorDetailsOf(ErrorField.USER_ID to userId),
                )

        refreshTokenJpaRepository.save(
            RefreshTokenEntity(
                tokenHash = refreshTokenHasher.hash(token),
                user = userEntity,
                expiresAt = expiresAt,
            ),
        )
    }

    override fun markRotated(
        token: String,
        rotatedAt: LocalDateTime,
    ) {
        val entity =
            refreshTokenJpaRepository.findByTokenHash(refreshTokenHasher.hash(token))
                ?: throw IllegalStateException("rotating refresh token not found")
        entity.markRotated(rotatedAt)
    }

    override fun deleteByUserId(userId: Long) {
        refreshTokenJpaRepository.deleteByUserId(userId)
    }

    override fun deleteByToken(token: String) {
        refreshTokenJpaRepository.deleteByTokenHash(refreshTokenHasher.hash(token))
    }
}
