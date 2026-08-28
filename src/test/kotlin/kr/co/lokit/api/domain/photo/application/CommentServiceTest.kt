package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.CommentRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.EmoticonRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.CommentListViewedEvent
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.domain.DeIdentifiedUserProfile
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoViewerRole
import kr.co.lokit.api.fixture.createComment
import kr.co.lokit.api.fixture.createCouple
import kr.co.lokit.api.fixture.createEmoticon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class CommentServiceTest {

    @Mock
    lateinit var commentRepository: CommentRepositoryPort

    @Mock
    lateinit var emoticonRepository: EmoticonRepositoryPort

    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    /**
     * B4 — CommentService 생성자 4번째 파라미터. @InjectMocks 라서 이 필드가 없으면
     * eventPublisher 에 null 이 주입되고 기존 테스트 전부가 NPE 로 깨진다.
     */
    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @InjectMocks
    lateinit var commentService: CommentService

    @Test
    fun `댓글을 생성할 수 있다`() {
        val savedComment = createComment(id = 1L, photoId = 10L, userId = 1L, content = "멋진 사진!")
        `when`(commentRepository.save(any())).thenReturn(savedComment)

        val result = commentService.createComment(10L, 1L, "멋진 사진!")

        assertEquals(1L, result.id)
        assertEquals(10L, result.photoId)
        assertEquals(1L, result.userId)
        assertEquals("멋진 사진!", result.content)
    }

    @Test
    fun `댓글을 생성하면 댓글 생성 이벤트가 발행된다`() {
        val savedComment = createComment(id = 7L, photoId = 10L, userId = 1L, content = "멋진 사진!")
        `when`(commentRepository.save(any())).thenReturn(savedComment)

        commentService.createComment(10L, 1L, "멋진 사진!")

        val captor = argumentCaptor<CommentCreatedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        assertEquals(7L, event.commentId)
        assertEquals(10L, event.photoId)
        assertEquals(1L, event.actorUserId)
    }

    @Test
    fun `이모지를 추가할 수 있다`() {
        val savedEmoticon = createEmoticon(id = 1L, commentId = 1L, userId = 1L, emoji = "❤️")
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(false)
        `when`(emoticonRepository.save(any())).thenReturn(savedEmoticon)
        // addEmoticon 이 이벤트의 photoId 를 얻으려 새로 호출한다(계약 2-10). 스텁이 없으면 NPE.
        `when`(commentRepository.findById(1L)).thenReturn(createComment(id = 1L, photoId = 10L, userId = 2L))

        val result = commentService.addEmoticon(1L, 1L, "❤️")

        assertEquals(1L, result.id)
        assertEquals("❤️", result.emoji)
    }

    @Test
    fun `이모지를 추가하면 사진 식별자를 담은 반응 이벤트가 발행된다`() {
        val savedEmoticon = createEmoticon(id = 5L, commentId = 1L, userId = 1L, emoji = "❤️")
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(false)
        `when`(emoticonRepository.save(any())).thenReturn(savedEmoticon)
        `when`(commentRepository.findById(1L)).thenReturn(createComment(id = 1L, photoId = 10L, userId = 2L))

        commentService.addEmoticon(1L, 1L, "❤️")

        val captor = argumentCaptor<EmoticonAddedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        assertEquals(5L, event.emoticonId)
        assertEquals(1L, event.commentId)
        assertEquals(10L, event.photoId)
        assertEquals(1L, event.actorUserId)
        assertEquals("❤️", event.emoji)
    }

    @Test
    fun `동일한 이모지를 중복 추가하면 예외가 발생한다`() {
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(true)

        assertThrows<BusinessException.EmoticonAlreadyExistsException> {
            commentService.addEmoticon(1L, 1L, "❤️")
        }
    }

    @Test
    fun `이모지를 제거할 수 있다`() {
        commentService.removeEmoticon(1L, 1L, "❤️")

        verify(emoticonRepository).delete(1L, 1L, "❤️")
    }

    @Test
    fun `댓글을 수정할 수 있다`() {
        val updated = createComment(id = 1L, photoId = 10L, userId = 1L, content = "수정된 댓글")
        `when`(commentRepository.update(1L, "수정된 댓글")).thenReturn(updated)

        val result = commentService.updateComment(1L, 1L, "수정된 댓글")

        assertEquals("수정된 댓글", result.content)
    }

    /**
     * RED-9 회귀. 알림은 "새로 생긴 것"에만 붙는다 — 수정/삭제는 알림 대상이 아니다.
     * createComment/addEmoticon 에 발행을 추가한 편집이 옆 메서드로 번지는 것을 막는 그물이다.
     */
    @Test
    fun `댓글을 수정해도 이벤트가 발행되지 않는다`() {
        val updated = createComment(id = 1L, photoId = 10L, userId = 1L, content = "수정된 댓글")
        `when`(commentRepository.update(1L, "수정된 댓글")).thenReturn(updated)

        commentService.updateComment(1L, 1L, "수정된 댓글")

        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    @Test
    fun `댓글을 삭제하면 완전히 사라진다`() {
        commentService.deleteComment(1L, 1L)

        verify(commentRepository).deleteHard(1L)
    }

    @Test
    fun `삭제한 댓글을 복구할 수 있다`() {
        val restored = createComment(id = 1L, photoId = 10L, userId = 1L)
        `when`(commentRepository.restoreDeleted(1L)).thenReturn(restored)

        val result = commentService.restoreComment(1L, 1L)

        assertEquals(1L, result.id)
        verify(commentRepository).restoreDeleted(1L)
    }

    @Test
    fun `삭제되지 않은 댓글은 복구할 수 없다`() {
        `when`(commentRepository.restoreDeleted(1L)).thenThrow(BusinessException.CommentNotDeletedException())

        assertThrows<BusinessException.CommentNotDeletedException> {
            commentService.restoreComment(1L, 1L)
        }
    }

    @Test
    fun `커플 연결 해제 시 끊은 사용자의 댓글이 비식별 처리된다`() {
        val disconnectedByUserId = 2L
        val viewerUserId = 1L
        val photoId = 10L

        val comments = listOf(
            CommentWithEmoticons(
                comment = createComment(id = 1L, userId = disconnectedByUserId, photoId = photoId),
                userName = "탈퇴한유저",
                userProfileImageUrl = "https://example.com/profile.jpg",
                emoticons = emptyList(),
            ),
            CommentWithEmoticons(
                comment = createComment(id = 2L, userId = viewerUserId, photoId = photoId),
                userName = "나",
                userProfileImageUrl = "https://example.com/my-profile.jpg",
                emoticons = emptyList(),
            ),
        )

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(comments)
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(
                id = 1L,
                userIds = listOf(viewerUserId, disconnectedByUserId),
                status = CoupleStatus.DISCONNECTED,
                disconnectedByUserId = disconnectedByUserId,
            ),
        )

        val result = commentService.getComments(photoId, viewerUserId)

        assertEquals(2, result.size)
        assertEquals(DeIdentifiedUserProfile.DISPLAY_NAME, result[0].userName)
        assertNull(result[0].userProfileImageUrl)
        assertEquals("나", result[1].userName)
        assertEquals("https://example.com/my-profile.jpg", result[1].userProfileImageUrl)
    }

    @Test
    fun `커플 연결 상태면 댓글이 비식별 처리되지 않는다`() {
        val viewerUserId = 1L
        val partnerUserId = 2L
        val photoId = 10L

        val comments = listOf(
            CommentWithEmoticons(
                comment = createComment(id = 1L, userId = partnerUserId, photoId = photoId),
                userName = "파트너",
                userProfileImageUrl = "https://example.com/partner.jpg",
                emoticons = emptyList(),
            ),
        )

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(comments)
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(
                id = 1L,
                userIds = listOf(viewerUserId, partnerUserId),
                status = CoupleStatus.CONNECTED,
            ),
        )

        val result = commentService.getComments(photoId, viewerUserId)

        assertEquals(1, result.size)
        assertEquals("파트너", result[0].userName)
        assertEquals("https://example.com/partner.jpg", result[0].userProfileImageUrl)
    }

    @Test
    fun `댓글 목록을 조회하면 조회 이벤트가 발행된다`() {
        val viewerUserId = 1L
        val photoOwnerId = 2L
        val photoId = 10L

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(emptyList())
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(id = 1L, userIds = listOf(viewerUserId, photoOwnerId), status = CoupleStatus.CONNECTED),
        )
        `when`(photoRepository.findUploaderIdById(photoId)).thenReturn(photoOwnerId)

        commentService.getComments(photoId, viewerUserId)

        val captor = argumentCaptor<CommentListViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        assertEquals(photoId, event.photoId)
        assertEquals(viewerUserId, event.viewerUserId)
        assertEquals(photoOwnerId, event.photoOwnerId)
        assertEquals(PhotoViewerRole.PARTNER, event.viewerRole)
    }

    @Test
    fun `댓글 목록 조회 이벤트에 댓글 수가 담긴다`() {
        val viewerUserId = 1L
        val photoOwnerId = 2L
        val photoId = 10L
        val comments = listOf(
            CommentWithEmoticons(
                comment = createComment(id = 1L, userId = photoOwnerId, photoId = photoId),
                userName = "업로더",
                userProfileImageUrl = null,
                emoticons = emptyList(),
            ),
            CommentWithEmoticons(
                comment = createComment(id = 2L, userId = viewerUserId, photoId = photoId),
                userName = "나",
                userProfileImageUrl = null,
                emoticons = emptyList(),
            ),
        )

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(comments)
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(id = 1L, userIds = listOf(viewerUserId, photoOwnerId), status = CoupleStatus.CONNECTED),
        )
        `when`(photoRepository.findUploaderIdById(photoId)).thenReturn(photoOwnerId)

        commentService.getComments(photoId, viewerUserId)

        val captor = argumentCaptor<CommentListViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(2, captor.firstValue.commentCount)
    }

    @Test
    fun `커플이 없어도 댓글 조회 이벤트는 발행된다`() {
        val viewerUserId = 1L
        val photoOwnerId = 2L
        val photoId = 10L

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(emptyList())
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(null)
        `when`(photoRepository.findUploaderIdById(photoId)).thenReturn(photoOwnerId)

        commentService.getComments(photoId, viewerUserId)

        val captor = argumentCaptor<CommentListViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(PhotoViewerRole.OTHER, captor.firstValue.viewerRole)
    }

    @Test
    fun `댓글 목록 조회는 업로더와 커플을 각각 한 번만 조회한다`() {
        val viewerUserId = 1L
        val photoOwnerId = 2L
        val photoId = 10L

        `when`(commentRepository.findAllByPhotoIdWithEmoticons(photoId, viewerUserId)).thenReturn(emptyList())
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(id = 1L, userIds = listOf(viewerUserId, photoOwnerId), status = CoupleStatus.CONNECTED),
        )
        `when`(photoRepository.findUploaderIdById(photoId)).thenReturn(photoOwnerId)

        commentService.getComments(photoId, viewerUserId)

        verify(coupleRepository, times(1)).findByUserId(viewerUserId)
        verify(photoRepository, times(1)).findUploaderIdById(photoId)
    }
}
