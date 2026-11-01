package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import kr.co.lokit.api.domain.photo.domain.CommentListViewedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoViewedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener

/**
 * photo_view/comment_view 원시 이벤트 기록. 조회 응답 경로에 영향을 주지 않도록 비동기로
 * 처리한다(계약 §1-5). notifId/notifType 은 알림 전용 필드라 항상 비운다(계약 §7-2).
 */
@Component
class ViewEventLogHandler(
    private val appEventLogPort: AppEventLogPort,
) {
    @Async
    @TransactionalEventListener
    fun handlePhotoViewed(event: PhotoViewedEvent) {
        appEventLogPort.record(
            eventName = "photo_view",
            userId = event.viewerUserId,
            notifId = null,
            notifType = null,
            params =
                mapOf(
                    "photo_id" to event.photoId,
                    "photo_owner_id" to event.photoOwnerId,
                    "viewer_role" to event.viewerRole.name,
                ),
        )
    }

    @Async
    @TransactionalEventListener
    fun handleCommentListViewed(event: CommentListViewedEvent) {
        appEventLogPort.record(
            eventName = "comment_view",
            userId = event.viewerUserId,
            notifId = null,
            notifType = null,
            params =
                mapOf(
                    "photo_id" to event.photoId,
                    "photo_owner_id" to event.photoOwnerId,
                    "viewer_role" to event.viewerRole.name,
                    "comment_count" to event.commentCount,
                ),
        )
    }
}
