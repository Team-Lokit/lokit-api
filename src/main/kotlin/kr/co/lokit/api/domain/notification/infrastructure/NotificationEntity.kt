package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.co.lokit.api.common.entity.BaseEntity
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationType
import java.time.LocalDateTime

/**
 * 유니크 제약을 두지 않는다(D1/F8): @SoftDelete와 유니크 제약 공존 시 소프트삭제 행이
 * 제약을 계속 점유하는 함정(슬라이스2 실측)을 피한다. notif_id는 UUIDv4라 충돌확률 무시 가능,
 * 인덱스만 건다. recipient/actor는 FK 아닌 raw Long(슬라이스2 DeviceTokenEntity와 동일 근거).
 */
@Entity(name = "Notification")
@Table(
    name = "notification",
    indexes = [
        Index(columnList = "recipient_user_id, target_photo_id, group_closed_at"),
        Index(columnList = "group_closed_at, sent_at"),
        Index(columnList = "notif_id"),
        Index(columnList = "recipient_user_id, sent_at"),
        Index(columnList = "sent_at"),
    ],
)
class NotificationEntity(
    @Column(name = "notif_id", nullable = false, length = 36)
    val notifId: String,
    @Column(name = "recipient_user_id", nullable = false)
    val recipientUserId: Long,
    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 32)
    val notificationType: NotificationType,
    @Column(name = "target_photo_id", nullable = false)
    val targetPhotoId: Long,
    @Column(name = "group_count", nullable = false)
    var groupCount: Int = 1,
    @Column(name = "title", nullable = false, length = 100)
    var title: String,
    @Column(name = "body", nullable = false, length = 200)
    var body: String,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
    @Column(name = "sent_at", nullable = false)
    val sentAt: LocalDateTime,
    @Column(name = "group_closed_at")
    var groupClosedAt: LocalDateTime? = null,
    @Column(name = "target_address", length = Notification.TARGET_ADDRESS_LENGTH)
    var targetAddress: String? = null,
) : BaseEntity()
