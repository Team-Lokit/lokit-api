package kr.co.lokit.api.domain.user.application.port

import java.time.LocalDateTime

data class RefreshTokenRecord(
    val userId: Long,
    val expiresAt: LocalDateTime,
    /** 다른 토큰으로 교체된 시각. null 이면 아직 현역 토큰. */
    val rotatedAt: LocalDateTime? = null,
)

interface RefreshTokenRepositoryPort {
    /** 행 잠금(SELECT ... FOR UPDATE)을 잡고 조회한다. 같은 토큰으로 동시에 들어온 갱신 요청을 직렬화한다. */
    fun findByTokenForUpdate(token: String): RefreshTokenRecord?

    fun save(
        userId: Long,
        token: String,
        expiresAt: LocalDateTime,
    )

    fun markRotated(
        token: String,
        rotatedAt: LocalDateTime,
    )

    fun deleteByUserId(userId: Long)

    fun deleteByToken(token: String)
}
