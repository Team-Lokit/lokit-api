package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import kr.co.lokit.api.fixture.createCouple
import kr.co.lokit.api.fixture.createPhoto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 수신자 = 행동한 사람의 커플 상대(사진 소유자 아님).
 * now 파라미터는 리스너가 넘기지 않는 기본값이므로 any() 로 받는다.
 */
@ExtendWith(MockitoExtension::class)
class CommentReactionNotificationListenerTest {
    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @Mock
    lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    lateinit var listener: CommentReactionNotificationListener

    @Test
    fun `상대 사진에 댓글을 달면 사진 주인인 상대에게 COMMENT 알림을 요청한다`() {
        givenPhoto(uploadedById = PARTNER_ID)
        givenCouple(userIds = listOf(PARTNER_ID, ACTOR_ID))

        listener.handleCommentCreated(commentEvent())

        verifyNotified(recipient = PARTNER_ID, type = NotificationType.COMMENT, emoji = null)
    }

    @Test
    fun `내 사진에 내가 답글을 달아도 상대에게 COMMENT 알림을 요청한다`() {
        givenPhoto(uploadedById = ACTOR_ID)
        givenCouple(userIds = listOf(ACTOR_ID, PARTNER_ID))

        listener.handleCommentCreated(commentEvent())

        verifyNotified(recipient = PARTNER_ID, type = NotificationType.COMMENT, emoji = null)
    }

    /** 버그2 픽스: 이모지 추가 이벤트가 담고 있던 emoji 값이 그대로 dispatch 서비스까지 전달된다. */
    @Test
    fun `이모지 추가 이벤트를 받으면 상대에게 REACTION 알림을 요청하고 이모지를 그대로 전달한다`() {
        givenPhoto(uploadedById = PARTNER_ID)
        givenCouple(userIds = listOf(PARTNER_ID, ACTOR_ID))

        listener.handleEmoticonAdded(emoticonEvent())

        verifyNotified(recipient = PARTNER_ID, type = NotificationType.REACTION, emoji = "❤️")
    }

    @Test
    fun `내 사진에 달린 상대 댓글에 내가 반응해도 상대에게 REACTION 알림을 요청한다`() {
        givenPhoto(uploadedById = ACTOR_ID)
        givenCouple(userIds = listOf(ACTOR_ID, PARTNER_ID))

        listener.handleEmoticonAdded(emoticonEvent())

        verifyNotified(recipient = PARTNER_ID, type = NotificationType.REACTION, emoji = "❤️")
    }

    @Test
    fun `행동한 사람이 사진 커플의 멤버가 아니면 알림을 만들지 않는다`() {
        // partnerIdFor 만 쓰면 userIds[0] 이 나와 남의 커플 구성원에게 푸시가 간다.
        givenPhoto(uploadedById = PARTNER_ID)
        givenCouple(userIds = listOf(PARTNER_ID, OTHER_ID))

        listener.handleCommentCreated(commentEvent())

        verifyNotNotified()
    }

    @Test
    fun `연결이 끊긴 커플이면 댓글 알림을 만들지 않는다`() {
        givenPhoto(uploadedById = PARTNER_ID)
        givenCouple(userIds = listOf(PARTNER_ID, ACTOR_ID), status = CoupleStatus.DISCONNECTED)

        listener.handleCommentCreated(commentEvent())

        verifyNotNotified()
    }

    @Test
    fun `만료된 커플이면 반응 알림을 만들지 않는다`() {
        givenPhoto(uploadedById = PARTNER_ID)
        givenCouple(userIds = listOf(PARTNER_ID, ACTOR_ID), status = CoupleStatus.EXPIRED)

        listener.handleEmoticonAdded(emoticonEvent())

        verifyNotNotified()
    }

    @Test
    fun `커플에 상대가 없으면 알림을 만들지 않는다`() {
        givenPhoto(uploadedById = ACTOR_ID)
        givenCouple(userIds = listOf(ACTOR_ID))

        listener.handleCommentCreated(commentEvent())

        verifyNotNotified()
    }

    @Test
    fun `사진에 커플 정보가 없으면 커플을 조회하지 않고 알림도 만들지 않는다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = ACTOR_ID, coupleId = null))

        listener.handleCommentCreated(commentEvent())

        verify(coupleRepository, never()).findById(any())
        verifyNotNotified()
    }

    @Test
    fun `사진의 커플을 찾을 수 없으면 알림을 만들지 않는다`() {
        givenPhoto(uploadedById = PARTNER_ID)
        whenever(coupleRepository.findById(COUPLE_ID)).thenReturn(null)

        listener.handleCommentCreated(commentEvent())

        verifyNotNotified()
    }

    @Test
    fun `사진 조회가 실패해도 예외가 전파되지 않는다`() {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenThrow(IllegalStateException("사진을 찾을 수 없습니다."))

        assertDoesNotThrow<Unit> { listener.handleCommentCreated(commentEvent()) }

        verifyNotNotified()
    }

    private fun givenPhoto(uploadedById: Long) {
        whenever(photoRepository.findById(PHOTO_ID))
            .thenReturn(createPhoto(id = PHOTO_ID, uploadedById = uploadedById, coupleId = COUPLE_ID))
    }

    private fun givenCouple(
        userIds: List<Long>,
        status: CoupleStatus = CoupleStatus.CONNECTED,
    ) {
        whenever(coupleRepository.findById(COUPLE_ID))
            .thenReturn(createCouple(id = COUPLE_ID, userIds = userIds, status = status))
    }

    private fun commentEvent() = CommentCreatedEvent(commentId = COMMENT_ID, photoId = PHOTO_ID, actorUserId = ACTOR_ID)

    private fun emoticonEvent() =
        EmoticonAddedEvent(
            emoticonId = EMOTICON_ID,
            commentId = COMMENT_ID,
            photoId = PHOTO_ID,
            actorUserId = ACTOR_ID,
            emoji = "❤️",
        )

    private fun verifyNotified(
        recipient: Long,
        type: NotificationType,
        emoji: String?,
    ) {
        verify(notificationDispatchService).notifyPhotoInteraction(
            recipientUserId = eq(recipient),
            actorUserId = eq(ACTOR_ID),
            targetPhotoId = eq(PHOTO_ID),
            notificationType = eq(type),
            emoji = eq(emoji),
            now = any(),
        )
    }

    private fun verifyNotNotified() {
        verify(notificationDispatchService, never())
            .notifyPhotoInteraction(any(), any(), any(), any(), anyOrNull(), any())
    }

    companion object {
        private const val PHOTO_ID = 10L
        private const val COUPLE_ID = 100L
        private const val COMMENT_ID = 1L
        private const val EMOTICON_ID = 5L
        private const val PARTNER_ID = 1L
        private const val ACTOR_ID = 2L
        private const val OTHER_ID = 3L
    }
}
