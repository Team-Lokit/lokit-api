package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.annotation.OptimisticRetry
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
 *
 * 예외 정책:
 * - schedule: 다른 인스턴스와의 @Version 충돌은 @OptimisticRetry 로 재조회부터 다시 한다.
 *   (같은 JVM 안의 경합은 LockManager 가 커밋 후 락을 풀어 직렬화한다.)
 * - fire: claim(markSent) 실패는 아무것도 커밋되지 않았으므로 예외를 그대로 던져 다음 주기에 재시도된다.
 *   claim 성공 후의 발송 준비(사진·이름 조회, 알림함 저장) 실패는 claim 을 되돌리지 않고
 *   이 자리에서 백오프 재시도한다 — claim 을 풀면 그 사이 생긴 새 배치와 겹쳐 중복/정체가 생긴다.
 *   재시도를 다 쓰면 error 로그로 남기고 포기한다(유실 지점이 로그에 반드시 남는다).
 */
@Service
class UploadNotificationService(
    private val pendingRepository: PendingUploadNotificationRepositoryPort,
    private val photoRepository: PhotoRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val notificationDispatchService: NotificationDispatchService,
    private val lockManager: LockManager,
    private val coupleConnectionChecker: CoupleConnectionChecker,
) : CancelPendingUploadNotificationsUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 열린 배치가 없으면 새 배치를 만들고, 있으면 사진을 덧붙이며 타이머를 리셋한다(B6).
     * scheduledAt 산술은 도메인(newBatch/withPhoto → scheduleFrom)이 전담한다 — 서비스는 now 만 넘긴다(D3).
     */
    @OptimisticRetry
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

        return dispatchWithRetry(claimed, now)
    }

    private fun dispatchWithRetry(
        claimed: PendingUploadNotification,
        now: LocalDateTime,
    ): Notification? {
        var lastError: Exception? = null
        for (attempt in 1..DISPATCH_MAX_ATTEMPTS) {
            try {
                // 연결 해제 시 미발송 배치는 취소되지만, 취소 직전에 claim 된 배치는 여기서 걸러진다.
                if (!coupleConnectionChecker.areConnectedPartners(claimed.recipientUserId, claimed.actorUserId)) {
                    log.info(
                        "커플 연결이 끊겨 업로드 알림 생략: pendingId={}, coupleId={}",
                        claimed.id,
                        claimed.coupleId,
                    )
                    return null
                }
                val survivors = photoRepository.findAllByIds(claimed.photoIds)
                if (survivors.isEmpty()) {
                    log.info("대상 사진이 전부 삭제됨 — 발송 생략: pendingId={}", claimed.id)
                    return null
                }
                return notificationDispatchService.dispatchImmediately(buildNotification(claimed, survivors, now))
            } catch (e: Exception) {
                lastError = e
                if (attempt < DISPATCH_MAX_ATTEMPTS) {
                    log.warn(
                        "업로드 알림 발송 준비 실패 — 재시도 {}/{}: pendingId={}",
                        attempt,
                        DISPATCH_MAX_ATTEMPTS,
                        claimed.id,
                        e,
                    )
                    sleepBackoff(attempt)
                }
            }
        }
        log.error(
            "업로드 알림 최종 실패 — 재시도 소진, 알림 누락: pendingId={}, coupleId={}, recipientUserId={}, photoIds={}",
            claimed.id,
            claimed.coupleId,
            claimed.recipientUserId,
            claimed.photoIds,
            lastError,
        )
        return null
    }

    private fun sleepBackoff(attempt: Int) {
        try {
            Thread.sleep(DISPATCH_BACKOFF_MILLIS * attempt)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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

    companion object {
        const val DISPATCH_MAX_ATTEMPTS: Int = 3
        const val DISPATCH_BACKOFF_MILLIS: Long = 200
    }
}
