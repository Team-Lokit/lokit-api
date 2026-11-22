package kr.co.lokit.api.domain.user.infrastructure

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import kr.co.lokit.api.common.concurrency.LockPolicy
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = LockPolicy.DB_PESSIMISTIC_LOCK_TIMEOUT_MILLIS_TEXT)])
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(tokenHash: String): RefreshTokenEntity?

    fun deleteByUser(user: UserEntity)

    fun deleteByTokenHash(tokenHash: String)

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    fun deleteByUserId(userId: Long)
}
