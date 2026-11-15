package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 수신자 = 행동한 사람(actor)의 커플 상대. 사진 소유자가 아니다.
 * 사진 소유자 기준이면 "주인이 자기 사진에 답글을 달 때", "주인이 상대 댓글에 반응할 때"
 * 상대가 알림을 못 받는다(수신자 = actor 라 self-차단에 걸림).
 *
 * 커플은 사진의 coupleId 로 찾는다(actor 의 현재 커플이 아니라 사진이 속한 커플).
 * 🔴 isMember 가드는 필수다 — Couple.partnerIdFor 는 actor 가 멤버가 아니어도 userIds[0] 을
 * 돌려주므로, 가드를 빼면 남의 커플 구성원에게 푸시가 간다(PhotoUploadNotificationListener 와 같은 이유).
 */
@Component
class CommentReactionNotificationListener(
    private val photoRepository: PhotoRepositoryPort,
    private val coupleRepository: CoupleRepositoryPort,
    private val notificationDispatchService: NotificationDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener
    fun handleCommentCreated(event: CommentCreatedEvent) {
        notify(event.photoId, event.actorUserId, NotificationType.COMMENT)
    }

    @Async
    @TransactionalEventListener
    fun handleEmoticonAdded(event: EmoticonAddedEvent) {
        notify(event.photoId, event.actorUserId, NotificationType.REACTION, event.emoji)
    }

    private fun notify(
        photoId: Long,
        actorUserId: Long,
        notificationType: NotificationType,
        emoji: String? = null,
    ) {
        try {
            val recipientUserId = resolveRecipient(photoId, actorUserId) ?: return
            notificationDispatchService.notifyPhotoInteraction(
                recipientUserId = recipientUserId,
                actorUserId = actorUserId,
                targetPhotoId = photoId,
                notificationType = notificationType,
                emoji = emoji,
            )
        } catch (e: Exception) {
            log.warn("알림 생성 실패 (사용자 요청에는 영향 없음): photoId={}, type={}", photoId, notificationType, e)
        }
    }

    private fun resolveRecipient(
        photoId: Long,
        actorUserId: Long,
    ): Long? {
        val coupleId =
            photoRepository.findById(photoId).coupleId ?: run {
                log.info("사진에 커플 정보가 없어 알림 생략: photoId={}, actorUserId={}", photoId, actorUserId)
                return null
            }
        val couple =
            coupleRepository.findById(coupleId) ?: run {
                log.info("사진의 커플을 찾을 수 없어 알림 생략: photoId={}, coupleId={}", photoId, coupleId)
                return null
            }
        if (!couple.isMember(actorUserId)) {
            log.warn(
                "행동한 사람이 사진 커플의 멤버가 아니어서 알림 생략: photoId={}, coupleId={}, actorUserId={}",
                photoId,
                coupleId,
                actorUserId,
            )
            return null
        }
        if (!couple.isConnected()) {
            log.info(
                "연결이 끊긴 커플이라 알림 생략: photoId={}, coupleId={}, status={}",
                photoId,
                coupleId,
                couple.status,
            )
            return null
        }
        return couple.partnerIdFor(actorUserId) ?: run {
            log.info("커플 상대가 없어 알림 생략: coupleId={}, actorUserId={}", coupleId, actorUserId)
            null
        }
    }
}
