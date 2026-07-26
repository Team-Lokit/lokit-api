package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.CommentRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.EmoticonRepositoryPort
import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.domain.DeIdentifiedUserProfile
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

@ExtendWith(MockitoExtension::class)
class CommentServiceTest {

    @Mock
    lateinit var commentRepository: CommentRepositoryPort

    @Mock
    lateinit var emoticonRepository: EmoticonRepositoryPort

    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

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
    fun `이모지를 추가할 수 있다`() {
        val savedEmoticon = createEmoticon(id = 1L, commentId = 1L, userId = 1L, emoji = "❤️")
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(false)
        `when`(emoticonRepository.countByCommentIdAndUserId(1L, 1L)).thenReturn(0)
        `when`(emoticonRepository.save(any())).thenReturn(savedEmoticon)

        val result = commentService.addEmoticon(1L, 1L, "❤️")

        assertEquals(1L, result.id)
        assertEquals("❤️", result.emoji)
    }

    @Test
    fun `동일한 이모지를 중복 추가하면 예외가 발생한다`() {
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(true)

        assertThrows<BusinessException.EmoticonAlreadyExistsException> {
            commentService.addEmoticon(1L, 1L, "❤️")
        }
    }

    @Test
    fun `이모지가 최대 개수를 초과하면 예외가 발생한다`() {
        `when`(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(1L, 1L, "❤️")).thenReturn(false)
        `when`(emoticonRepository.countByCommentIdAndUserId(1L, 1L)).thenReturn(10)

        assertThrows<BusinessException.CommentMaxEmoticonsExceededException> {
            commentService.addEmoticon(1L, 1L, "❤️")
        }
    }

    @Test
    fun `이모지를 제거할 수 있다`() {
        commentService.removeEmoticon(1L, 1L, "❤️")

        verify(emoticonRepository).delete(1L, 1L, "❤️")
    }

    @Test
    fun `답글을 생성할 수 있다`() {
        val parent = createComment(id = 1L, photoId = 10L, userId = 1L)
        val savedReply = createComment(id = 2L, photoId = 10L, userId = 2L, parentId = 1L, content = "답글")
        `when`(commentRepository.findById(1L)).thenReturn(parent)
        `when`(commentRepository.save(any())).thenReturn(savedReply)

        val result = commentService.createReply(1L, 2L, "답글")

        assertEquals(1L, result.parentId)
        assertEquals("답글", result.content)
    }

    @Test
    fun `답글에 답글을 작성하면 예외가 발생한다`() {
        val reply = createComment(id = 2L, photoId = 10L, userId = 1L, parentId = 1L)
        `when`(commentRepository.findById(2L)).thenReturn(reply)

        assertThrows<BusinessException.ReplyDepthExceededException> {
            commentService.createReply(2L, 3L, "답글의 답글")
        }
    }

    @Test
    fun `삭제된 댓글에는 답글을 작성할 수 없다`() {
        val removedComment = createComment(id = 1L, photoId = 10L, userId = 1L, removed = true)
        `when`(commentRepository.findById(1L)).thenReturn(removedComment)

        assertThrows<BusinessException.ReplyToRemovedNotAllowedException> {
            commentService.createReply(1L, 2L, "답글")
        }
    }

    @Test
    fun `댓글을 수정할 수 있다`() {
        val existing = createComment(id = 1L, photoId = 10L, userId = 1L)
        val updated = createComment(id = 1L, photoId = 10L, userId = 1L, content = "수정된 댓글")
        `when`(commentRepository.findById(1L)).thenReturn(existing)
        `when`(commentRepository.update(1L, "수정된 댓글")).thenReturn(updated)

        val result = commentService.updateComment(1L, 1L, "수정된 댓글")

        assertEquals("수정된 댓글", result.content)
    }

    @Test
    fun `이미 삭제된 댓글은 수정할 수 없다`() {
        val removedComment = createComment(id = 1L, photoId = 10L, userId = 1L, removed = true)
        `when`(commentRepository.findById(1L)).thenReturn(removedComment)

        assertThrows<BusinessException.CommentAlreadyRemovedException> {
            commentService.updateComment(1L, 1L, "수정 시도")
        }
    }

    @Test
    fun `답글이 없는 최상위 댓글을 삭제하면 완전히 사라진다`() {
        val existing = createComment(id = 1L, photoId = 10L, userId = 1L)
        `when`(commentRepository.findById(1L)).thenReturn(existing)
        `when`(commentRepository.countRepliesByParentId(1L)).thenReturn(0L)

        commentService.deleteComment(1L, 1L)

        verify(commentRepository).deleteHard(1L)
        verify(commentRepository, never()).markRemoved(any(), any())
    }

    @Test
    fun `답글이 있는 최상위 댓글을 삭제하면 플레이스홀더로 치환된다`() {
        val existing = createComment(id = 1L, photoId = 10L, userId = 1L)
        `when`(commentRepository.findById(1L)).thenReturn(existing)
        `when`(commentRepository.countRepliesByParentId(1L)).thenReturn(2L)

        commentService.deleteComment(1L, 1L)

        verify(commentRepository).markRemoved(1L, Comment.REMOVED_PLACEHOLDER_TEXT)
        verify(commentRepository, never()).deleteHard(any())
    }

    @Test
    fun `답글을 삭제하면 완전히 사라진다`() {
        val reply = createComment(id = 2L, photoId = 10L, userId = 1L, parentId = 1L)
        `when`(commentRepository.findById(2L)).thenReturn(reply)

        commentService.deleteComment(2L, 1L)

        verify(commentRepository).deleteHard(2L)
        verify(commentRepository, never()).countRepliesByParentId(any())
    }

    @Test
    fun `중첩된 답글도 작성자에 따라 독립적으로 비식별 처리된다`() {
        val disconnectedByUserId = 2L
        val viewerUserId = 1L
        val photoId = 10L

        val comments =
            listOf(
                CommentWithEmoticons(
                    comment = createComment(id = 1L, userId = viewerUserId, photoId = photoId),
                    userName = "나",
                    userProfileImageUrl = "https://example.com/my-profile.jpg",
                    emoticons = emptyList(),
                    replies =
                        listOf(
                            CommentWithEmoticons(
                                comment = createComment(id = 2L, userId = disconnectedByUserId, photoId = photoId, parentId = 1L),
                                userName = "탈퇴한유저",
                                userProfileImageUrl = "https://example.com/profile.jpg",
                                emoticons = emptyList(),
                            ),
                        ),
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

        assertEquals("나", result[0].userName)
        assertEquals(DeIdentifiedUserProfile.DISPLAY_NAME, result[0].replies[0].userName)
        assertNull(result[0].replies[0].userProfileImageUrl)
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
}
