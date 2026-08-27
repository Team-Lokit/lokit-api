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

    /** 동률 시 id 내림차순 2차 정렬 — 없으면 offset 페이지 경계에서 행이 중복/누락된다(B7). */
    fun findAllByRecipientUserIdOrderBySentAtDescIdDesc(
        recipientUserId: Long,
        pageable: Pageable,
    ): List<NotificationEntity>

    fun countByRecipientUserId(recipientUserId: Long): Long

    /** notif_id는 UUIDv4라 유니크 제약이 없어도 사실상 단건이다. */
    fun findByNotifId(notifId: String): NotificationEntity?

    /** Before = strictly less than. LessThanEqual로 쓰면 경계 1건이 더 지워진다. */
    fun findAllBySentAtBeforeOrderBySentAtAsc(
        sentAt: LocalDateTime,
        pageable: Pageable,
    ): List<NotificationEntity>
}
