package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import kr.co.lokit.api.fixture.createPhoto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 계약 T8. 협력자가 2개뿐이라 @InjectMocks 를 쓴다 — 슬라이스5의 LockManager 실 인스턴스 요구는
 * NotificationDispatchService 내부 사정이고, 여기서는 그 서비스를 통째로 목으로 세운다.
 * now 파라미터는 리스너가 넘기지 않는 기본값이므로 any() 로 받는다.
 */
@ExtendWith(MockitoExtension::class)
class CommentReactionNotificationListenerTest {
    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @Mock
    lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    lateinit var listener: CommentReactionNotificationListener

    @Test
    fun `댓글 생성 이벤트를 받으면 사진 소유자에게 COMMENT 알림을 요청한다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = OWNER_ID))

        listener.handleCommentCreated(
            CommentCreatedEvent(commentId = COMMENT_ID, photoId = PHOTO_ID, actorUserId = ACTOR_ID),
        )

        verify(notificationDispatchService).notifyPhotoInteraction(
            recipientUserId = eq(OWNER_ID),
            actorUserId = eq(ACTOR_ID),
            targetPhotoId = eq(PHOTO_ID),
            notificationType = eq(NotificationType.COMMENT),
            now = any(),
        )
    }

    @Test
    fun `이모지 추가 이벤트를 받으면 REACTION 알림을 요청한다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = OWNER_ID))

        listener.handleEmoticonAdded(
            EmoticonAddedEvent(
                emoticonId = EMOTICON_ID,
                commentId = COMMENT_ID,
                photoId = PHOTO_ID,
                actorUserId = ACTOR_ID,
                emoji = "❤️",
            ),
        )

        verify(notificationDispatchService).notifyPhotoInteraction(
            recipientUserId = eq(OWNER_ID),
            actorUserId = eq(ACTOR_ID),
            targetPhotoId = eq(PHOTO_ID),
            notificationType = eq(NotificationType.REACTION),
            now = any(),
        )
    }

    @Test
    fun `내 사진에 내가 댓글을 달면 알림을 만들지 않는다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = ACTOR_ID))

        listener.handleCommentCreated(
            CommentCreatedEvent(commentId = COMMENT_ID, photoId = PHOTO_ID, actorUserId = ACTOR_ID),
        )

        verify(notificationDispatchService, never())
            .notifyPhotoInteraction(any(), any(), any(), any(), any())
    }

    @Test
    fun `내 사진에 내가 반응을 남겨도 알림을 만들지 않는다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = ACTOR_ID))

        listener.handleEmoticonAdded(
            EmoticonAddedEvent(
                emoticonId = EMOTICON_ID,
                commentId = COMMENT_ID,
                photoId = PHOTO_ID,
                actorUserId = ACTOR_ID,
                emoji = "❤️",
            ),
        )

        verify(notificationDispatchService, never())
            .notifyPhotoInteraction(any(), any(), any(), any(), any())
    }

    @Test
    fun `사진 조회가 실패해도 예외가 전파되지 않는다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenThrow(IllegalStateException("사진을 찾을 수 없습니다."))

        assertDoesNotThrow<Unit> {
            listener.handleCommentCreated(
                CommentCreatedEvent(commentId = COMMENT_ID, photoId = PHOTO_ID, actorUserId = ACTOR_ID),
            )
        }

        verify(notificationDispatchService, never())
            .notifyPhotoInteraction(any(), any(), any(), any(), any())
    }

    companion object {
        private const val PHOTO_ID = 10L
        private const val COMMENT_ID = 1L
        private const val EMOTICON_ID = 5L
        private const val OWNER_ID = 1L
        private const val ACTOR_ID = 2L
    }
}
