package kr.co.lokit.api.domain.notification.application.port

import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import java.time.LocalDateTime

/** 포트는 '10분' 정책을 모른다(D3). findDuePendings 인자는 now 그 자체다. */
interface PendingUploadNotificationRepositoryPort {
    fun findUnsentByCoupleAndActor(coupleId: Long, actorUserId: Long): PendingUploadNotification?

    fun save(pending: PendingUploadNotification): PendingUploadNotification

    fun appendPhotoAndReschedule(
        id: Long,
        photoIds: List<Long>,
        scheduledAt: LocalDateTime,
    ): PendingUploadNotification

    fun findDuePendings(scheduledAtBefore: LocalDateTime, limit: Int): List<PendingUploadNotification>

    fun markSent(id: Long, sentAt: LocalDateTime): PendingUploadNotification

    fun deleteUnsentByCoupleId(coupleId: Long): Int
}
