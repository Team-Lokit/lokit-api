package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.concurrency.LockManager
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.`in`.CancelPendingUploadNotificationsUseCase
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationMessage
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.Photo
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * N-2 업로드 알림(10분 debounce 지연발송) 서비스.
 *
 * 클래스에 @Transactional 을 붙이지 않는다 — LockManager.withLock 이 REQUIRES_NEW 로 건다(F14).
 * photoRepository/userRepository/notificationDispatchService 는 다음 사이클의 fire 가 쓴다.
 */
@Service
class UploadNotificationService(
    private val pendingRepository: PendingUploadNotificationRepositoryPort,
    private val photoRepository: PhotoRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val notificationDispatchService: NotificationDispatchService,
    private val lockManager: LockManager,
) : CancelPendingUploadNotificationsUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 열린 배치가 없으면 새 배치를 만들고, 있으면 사진을 덧붙이며 타이머를 리셋한다(B6).
     * scheduledAt 산술은 도메인(newBatch/withPhoto → scheduleFrom)이 전담한다 — 서비스는 now 만 넘긴다(D3).
     */
    fun schedule(
        coupleId: Long,
        recipientUserId: Long,
        actorUserId: Long,
        photoId: Long,
        now: LocalDateTime = LocalDateTime.now(),
    ): PendingUploadNotification =
        lockManager.withLock(
            key = PendingUploadNotification.lockKey(coupleId, actorUserId),
            operation = {
                val existing = pendingRepository.findUnsentByCoupleAndActor(coupleId, actorUserId)
                if (existing == null) {
                    pendingRepository.save(
                        PendingUploadNotification.newBatch(coupleId, recipientUserId, actorUserId, photoId, now),
                    )
                } else {
                    val reset = existing.withPhoto(photoId, now)
                    pendingRepository.appendPhotoAndReschedule(reset.id, reset.photoIds, reset.scheduledAt)
                }
            },
        )

    /**
     * claim-then-send: 락 안 재조회로 isDue 를 다시 확인(리셋이 스케줄러를 이긴다) →
     * markSent(claim 이 dispatch 보다 먼저, G-5) → 사진 재조회 → 전부 삭제면 미발송(B7).
     */
    fun fire(
        pending: PendingUploadNotification,
        now: LocalDateTime = LocalDateTime.now(),
    ): Notification? {
        val claimed =
            lockManager.withLock(
                key = PendingUploadNotification.lockKey(pending.coupleId, pending.actorUserId),
                operation = {
                    val fresh =
                        pendingRepository.findUnsentByCoupleAndActor(pending.coupleId, pending.actorUserId)
                            ?.takeIf { it.id == pending.id && it.isDue(now) }
                            ?: return@withLock null
                    pendingRepository.markSent(fresh.id, now)
                },
            ) ?: return null

        val survivors = photoRepository.findAllByIds(claimed.photoIds)
        if (survivors.isEmpty()) {
            log.debug("대상 사진이 전부 삭제됨 — 발송 생략: pendingId={}", claimed.id)
            return null
        }
        return notificationDispatchService.dispatchImmediately(buildNotification(claimed, survivors, now))
    }

    override fun cancelByCoupleId(coupleId: Long): Int = pendingRepository.deleteUnsentByCoupleId(coupleId)

    /** 대표 사진·공통 주소는 claimed.photoIds 순서 기준(findAllByIds 는 순서 미보장, D6). */
    private fun buildNotification(
        claimed: PendingUploadNotification,
        survivors: List<Photo>,
        now: LocalDateTime,
    ): Notification {
        val ordered = claimed.photoIds.mapNotNull { id -> survivors.firstOrNull { it.id == id } }
        val address = PendingUploadNotification.commonAddressOf(ordered.map { it.address })
        return Notification.upload(
            notifId = UUID.randomUUID().toString(),
            recipientUserId = claimed.recipientUserId,
            actorUserId = claimed.actorUserId,
            targetPhotoId = ordered.last().id,
            targetAddress = address,
            photoCount = ordered.size,
            title = NotificationMessage.title(NotificationType.UPLOAD),
            body = NotificationMessage.uploadBody(resolveActorName(claimed.actorUserId), ordered.size, address),
            sentAt = now,
        )
    }

    private fun resolveActorName(actorUserId: Long): String =
        userRepository.findById(actorUserId)?.name ?: NotificationMessage.DEFAULT_ACTOR_NAME
}
