package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.co.lokit.api.common.entity.BaseEntity
import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import java.time.LocalDateTime

/**
 * 유니크 제약을 두지 않는다(F15): @SoftDelete와 유니크 제약 공존 시 소프트삭제 행이 제약을
 * 계속 점유하는 함정을 피한다. 동시성 방어는 LockManager가 전담한다(D3).
 * couple/user는 FK 아닌 raw Long. photo_ids는 구분자 문자열이다(D2 — JSON/Converter 선례 0건).
 *
 * @Entity(name = "PendingUploadNotification")의 이름은 JpaRepository의 HQL
 * `delete from PendingUploadNotification p ...`가 참조하는 엔티티명이다 — 바꾸면 런타임에 깨진다.
 */
@Entity(name = "PendingUploadNotification")
@Table(
    name = "pending_upload_notification",
    indexes = [
        Index(columnList = "couple_id, actor_user_id, sent_at"),
        Index(columnList = "sent_at, scheduled_at"),
        Index(columnList = "couple_id, sent_at"),
    ],
)
class PendingUploadNotificationEntity(
    @Column(name = "couple_id", nullable = false)
    val coupleId: Long,
    @Column(name = "recipient_user_id", nullable = false)
    val recipientUserId: Long,
    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,
    @Column(
        name = "photo_ids",
        nullable = false,
        length = PendingUploadNotification.PHOTO_IDS_COLUMN_LENGTH,
    )
    var photoIds: String,
    @Column(name = "scheduled_at", nullable = false)
    var scheduledAt: LocalDateTime,
    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null,
) : BaseEntity()
