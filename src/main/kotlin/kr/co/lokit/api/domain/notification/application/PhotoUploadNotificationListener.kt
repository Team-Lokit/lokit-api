package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.photo.domain.PhotoCreatedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 업로드 이벤트를 받아 수신자를 해석하고 발송을 예약한다(계약 2-16).
 *
 * 🔴 userIds.contains 가드는 필수 방어선이다. Couple.partnerIdFor 는
 * `userIds.firstOrNull { it != userId }` 라서 업로더가 그 커플의 멤버가 아니면
 * null 이 아니라 userIds[0] 을 돌려준다 — 가드를 빼면 남의 커플 구성원에게 푸시가 간다.
 */
@Component
class PhotoUploadNotificationListener(
    private val coupleRepository: CoupleRepositoryPort,
    private val uploadNotificationService: UploadNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener
    fun handlePhotoCreated(event: PhotoCreatedEvent) {
        try {
            val couple =
                coupleRepository.findById(event.coupleId)
                    ?.takeIf { it.userIds.contains(event.uploaderUserId) } ?: return
            val recipientUserId = couple.partnerIdFor(event.uploaderUserId) ?: return
            uploadNotificationService.schedule(
                coupleId = event.coupleId,
                recipientUserId = recipientUserId,
                actorUserId = event.uploaderUserId,
                photoId = event.photoId,
            )
        } catch (e: Exception) {
            log.warn(
                "업로드 알림 예약 실패 (사용자 요청에는 영향 없음): photoId={}, coupleId={}",
                event.photoId,
                event.coupleId,
                e,
            )
        }
    }
}
