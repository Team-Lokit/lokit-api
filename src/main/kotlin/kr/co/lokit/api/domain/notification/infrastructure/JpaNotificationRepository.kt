package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.common.exception.entityNotFound
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationType
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

    @Transactional(readOnly = true)
    override fun findLatestUnclosedByRecipientAndPhoto(
        recipientUserId: Long,
        targetPhotoId: Long,
        notificationType: NotificationType,
    ): Notification? =
        notificationJpaRepository
            .findFirstByRecipientUserIdAndTargetPhotoIdAndNotificationTypeAndGroupClosedAtIsNullOrderBySentAtDesc(
                recipientUserId,
                targetPhotoId,
                notificationType,
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
    @Transactional(readOnly = true)
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

    /** 정렬은 파생 쿼리 이름이 담당한다 — PageRequest에 Sort를 얹지 않는다(B16). */
    @Transactional(readOnly = true)
    override fun findInboxPage(recipientUserId: Long, page: Int, size: Int): List<Notification> =
        notificationJpaRepository
            .findAllByRecipientUserIdOrderBySentAtDescIdDesc(recipientUserId, PageRequest.of(page, size))
            .map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun countInbox(recipientUserId: Long): Long =
        notificationJpaRepository.countByRecipientUserId(recipientUserId)

    @Transactional(readOnly = true)
    override fun findByNotifId(notifId: String): Notification? =
        notificationJpaRepository.findByNotifId(notifId)?.toDomain()

    /** 더티체킹 — save를 부르지 않는다(계약 2-8). */
    @Transactional
    override fun markAsRead(notificationId: Long): Notification {
        val entity = notificationJpaRepository.findByIdOrNull(notificationId)
            ?: throw entityNotFound<Notification>(notificationId)
        entity.isRead = true
        return entity.toDomain()
    }

    /** deleteAll(entities)를 쓴다 — @SoftDelete가 DELETE를 is_deleted=true UPDATE로 바꾼다. */
    @Transactional
    override fun deleteSentBefore(sentAtBefore: LocalDateTime, limit: Int): Int {
        val targets = notificationJpaRepository
            .findAllBySentAtBeforeOrderBySentAtAsc(sentAtBefore, PageRequest.of(0, limit))
        if (targets.isEmpty()) return 0
        notificationJpaRepository.deleteAll(targets)
        return targets.size
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
