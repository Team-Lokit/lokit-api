package kr.co.lokit.api.domain.notification.infrastructure

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 네이티브 SQL 불필요 — 유니크 제약이 없어 @SoftDelete 우회가 필요 없다.
 * Hibernate가 is_deleted=false 조건을 자동으로 추가한다.
 */
interface NotificationJpaRepository : JpaRepository<NotificationEntity, Long> {
    fun findFirstByRecipientUserIdAndTargetPhotoIdAndGroupClosedAtIsNullOrderBySentAtDesc(
        recipientUserId: Long,
        targetPhotoId: Long,
    ): NotificationEntity?

    fun findAllByGroupClosedAtIsNullAndSentAtLessThanEqualOrderBySentAtAsc(
        sentAt: LocalDateTime,
        pageable: Pageable,
    ): List<NotificationEntity>
}
