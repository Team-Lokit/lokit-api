package kr.co.lokit.api.domain.notification.infrastructure

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

/**
 * 정렬과 필터는 전부 파생 쿼리 이름이 전담한다 — 호출자가 Pageable에 Sort를 겹치지 않는다.
 */
interface PendingUploadNotificationJpaRepository : JpaRepository<PendingUploadNotificationEntity, Long> {
    fun findFirstByCoupleIdAndActorUserIdAndSentAtIsNull(
        coupleId: Long,
        actorUserId: Long,
    ): PendingUploadNotificationEntity?

    fun findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
        scheduledAt: LocalDateTime,
        pageable: Pageable,
    ): List<PendingUploadNotificationEntity>

    /** HQL delete는 @SoftDelete로 UPDATE 재작성된다(F16) — 여기선 이게 원하는 동작이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PendingUploadNotification p where p.coupleId = :coupleId and p.sentAt is null")
    fun deleteUnsentByCoupleId(coupleId: Long): Int
}
