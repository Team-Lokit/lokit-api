package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.common.exception.entityNotFound
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JpaPendingUploadNotificationRepository(
    private val pendingUploadNotificationJpaRepository: PendingUploadNotificationJpaRepository,
) : PendingUploadNotificationRepositoryPort {
    @Transactional(readOnly = true)
    override fun findUnsentByCoupleAndActor(coupleId: Long, actorUserId: Long): PendingUploadNotification? =
        pendingUploadNotificationJpaRepository
            .findFirstByCoupleIdAndActorUserIdAndSentAtIsNull(coupleId, actorUserId)
            ?.toDomain()

    @Transactional
    override fun save(pending: PendingUploadNotification): PendingUploadNotification =
        pendingUploadNotificationJpaRepository.save(
            PendingUploadNotificationEntity(
                coupleId = pending.coupleId,
                recipientUserId = pending.recipientUserId,
                actorUserId = pending.actorUserId,
                photoIds = PendingUploadNotification.encodePhotoIds(pending.photoIds),
                scheduledAt = pending.scheduledAt,
                sentAt = pending.sentAt,
            ),
        ).toDomain()

    /** 더티체킹 — save를 부르지 않는다(계약 2-11). */
    @Transactional
    override fun appendPhotoAndReschedule(
        id: Long,
        photoIds: List<Long>,
        scheduledAt: LocalDateTime,
    ): PendingUploadNotification {
        val entity = pendingUploadNotificationJpaRepository.findByIdOrNull(id)
            ?: throw entityNotFound<PendingUploadNotification>(id)
        entity.photoIds = PendingUploadNotification.encodePhotoIds(photoIds)
        entity.scheduledAt = scheduledAt
        return entity.toDomain()
    }

    /**
     * 상한은 scheduledAtBefore 그 자체다 — 여기서 빼거나 더하지 않는다(D3/B11).
     * 정렬은 파생 쿼리 이름의 OrderByScheduledAtAsc가 담당한다 — PageRequest에 Sort를 얹지 않는다.
     */
    @Transactional(readOnly = true)
    override fun findDuePendings(scheduledAtBefore: LocalDateTime, limit: Int): List<PendingUploadNotification> =
        pendingUploadNotificationJpaRepository
            .findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                scheduledAtBefore,
                PageRequest.of(0, limit),
            ).map { it.toDomain() }

    /** 더티체킹 — save를 부르지 않는다(계약 2-11). */
    @Transactional
    override fun markSent(id: Long, sentAt: LocalDateTime): PendingUploadNotification {
        val entity = pendingUploadNotificationJpaRepository.findByIdOrNull(id)
            ?: throw entityNotFound<PendingUploadNotification>(id)
        entity.sentAt = sentAt
        return entity.toDomain()
    }

    @Transactional
    override fun deleteUnsentByCoupleId(coupleId: Long): Int =
        pendingUploadNotificationJpaRepository.deleteUnsentByCoupleId(coupleId)

    private fun PendingUploadNotificationEntity.toDomain(): PendingUploadNotification =
        PendingUploadNotification(
            id = id ?: 0L,
            coupleId = coupleId,
            recipientUserId = recipientUserId,
            actorUserId = actorUserId,
            photoIds = PendingUploadNotification.decodePhotoIds(photoIds),
            scheduledAt = scheduledAt,
            sentAt = sentAt,
        )
}
