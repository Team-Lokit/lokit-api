package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.common.exception.entityNotFound
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JpaNotificationRepository(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationRepositoryPort {
    @Transactional
    override fun save(notification: Notification): Notification =
        notificationJpaRepository.save(
            NotificationEntity(
                notifId = notification.notifId,
                recipientUserId = notification.recipientUserId,
                actorUserId = notification.actorUserId,
                notificationType = notification.notificationType,
                targetPhotoId = notification.targetPhotoId,
                groupCount = notification.groupCount,
                title = notification.title,
                body = notification.body,
                isRead = notification.isRead,
                sentAt = notification.sentAt,
                groupClosedAt = notification.groupClosedAt,
                targetAddress = notification.targetAddress,
            ),
        ).toDomain()

    override fun findLatestUnclosedByRecipientAndPhoto(recipientUserId: Long, targetPhotoId: Long): Notification? =
        notificationJpaRepository.findFirstByRecipientUserIdAndTargetPhotoIdAndGroupClosedAtIsNullOrderBySentAtDesc(
            recipientUserId,
            targetPhotoId,
        )?.toDomain()

    /** 더티체킹 — save를 부르지 않는다(계약 2-8). */
    @Transactional
    override fun increaseGroupCount(notificationId: Long): Notification {
        val entity = notificationJpaRepository.findByIdOrNull(notificationId)
            ?: throw entityNotFound<Notification>(notificationId)
        entity.groupCount += 1
        return entity.toDomain()
    }

    /** 정렬은 파생 쿼리 이름의 OrderBySentAtAsc가 담당한다 — PageRequest에 Sort를 얹지 않는다(B16). */
    override fun findClosableGroupWindows(sentAtBefore: LocalDateTime, limit: Int): List<Notification> =
        notificationJpaRepository.findAllByGroupClosedAtIsNullAndSentAtLessThanEqualOrderBySentAtAsc(
            sentAtBefore,
            PageRequest.of(0, limit),
        ).map { it.toDomain() }

    /** 더티체킹 — save를 부르지 않는다(계약 2-8). */
    @Transactional
    override fun closeGroupWindow(notificationId: Long, closedAt: LocalDateTime, body: String): Notification {
        val entity = notificationJpaRepository.findByIdOrNull(notificationId)
            ?: throw entityNotFound<Notification>(notificationId)
        entity.groupClosedAt = closedAt
        entity.body = body
        return entity.toDomain()
    }

    private fun NotificationEntity.toDomain(): Notification =
        Notification(
            id = id ?: 0L,
            notifId = notifId,
            recipientUserId = recipientUserId,
            actorUserId = actorUserId,
            notificationType = notificationType,
            targetPhotoId = targetPhotoId,
            groupCount = groupCount,
            title = title,
            body = body,
            isRead = isRead,
            sentAt = sentAt,
            groupClosedAt = groupClosedAt,
            targetAddress = targetAddress,
        )
}
