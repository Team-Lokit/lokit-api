package kr.co.lokit.api.domain.user.application.port

import java.time.LocalDateTime

data class RefreshTokenRecord(
    val userId: Long,
    val expiresAt: LocalDateTime,
)

interface RefreshTokenRepositoryPort {
    fun findByToken(token: String): RefreshTokenRecord?

    fun save(
        userId: Long,
        token: String,
        expiresAt: LocalDateTime,
    )

    fun rotate(
        oldToken: String,
        newToken: String,
        expiresAt: LocalDateTime,
    )

    fun deleteByUserId(userId: Long)

    fun deleteByToken(token: String)
}
