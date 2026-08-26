package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/** 수신자=사진 소유자(uploadedById). 커플 2인이므로 actor!=recipient 비교만으로 self-차단 충분. */
@Component
class CommentReactionNotificationListener(
    private val photoRepository: PhotoRepositoryPort,
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
        notify(event.photoId, event.actorUserId, NotificationType.REACTION)
    }

    private fun notify(
        photoId: Long,
        actorUserId: Long,
        notificationType: NotificationType,
    ) {
        try {
            val recipientUserId = photoRepository.findById(photoId).uploadedById
            if (recipientUserId == actorUserId) return
            notificationDispatchService.notifyPhotoInteraction(
                recipientUserId = recipientUserId,
                actorUserId = actorUserId,
                targetPhotoId = photoId,
                notificationType = notificationType,
            )
        } catch (e: Exception) {
            log.warn("알림 생성 실패 (사용자 요청에는 영향 없음): photoId={}, type={}", photoId, notificationType, e)
        }
    }
}
