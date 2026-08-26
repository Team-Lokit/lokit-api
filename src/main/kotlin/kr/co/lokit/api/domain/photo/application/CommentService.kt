package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.CommentRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.EmoticonRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.`in`.CommentUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.EmoticonUseCase
import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.CommentCreatedEvent
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.domain.Emoticon
import kr.co.lokit.api.domain.photo.domain.EmoticonAddedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val commentRepository: CommentRepositoryPort,
    private val emoticonRepository: EmoticonRepositoryPort,
    private val coupleRepository: CoupleRepositoryPort,
    private val eventPublisher: ApplicationEventPublisher,
) : CommentUseCase,
    EmoticonUseCase {
    @Transactional
    override fun createComment(
        photoId: Long,
        userId: Long,
        content: String,
    ): Comment {
        val comment = Comment(photoId = photoId, userId = userId, content = content)
        val saved = commentRepository.save(comment)
        eventPublisher.publishEvent(
            CommentCreatedEvent(commentId = saved.id, photoId = saved.photoId, actorUserId = saved.userId),
        )
        return saved
    }

    @Transactional(readOnly = true)
    override fun getComments(
        photoId: Long,
        currentUserId: Long,
    ): List<CommentWithEmoticons> {
        val comments = commentRepository.findAllByPhotoIdWithEmoticons(photoId, currentUserId)
        val deIdentifyUserId = coupleRepository.findByUserId(currentUserId)?.deIdentifiedUserId()
            ?: return comments
        return comments.map { if (it.comment.userId == deIdentifyUserId) it.deIdentified() else it }
    }

    @Transactional
    override fun updateComment(
        commentId: Long,
        userId: Long,
        content: String,
    ): Comment = commentRepository.update(commentId, content)

    @Transactional
    override fun deleteComment(
        commentId: Long,
        userId: Long,
    ) {
        commentRepository.deleteHard(commentId)
    }

    @Transactional
    override fun restoreComment(
        commentId: Long,
        userId: Long,
    ): Comment = commentRepository.restoreDeleted(commentId)

    @Transactional
    override fun addEmoticon(
        commentId: Long,
        userId: Long,
        emoji: String,
    ): Emoticon {
        if (emoticonRepository.existsByCommentIdAndUserIdAndEmoji(commentId, userId, emoji)) {
            throw BusinessException.EmoticonAlreadyExistsException(
                errors =
                    errorDetailsOf(
                        ErrorField.COMMENT_ID to commentId,
                        ErrorField.USER_ID to userId,
                        ErrorField.EMOJI to emoji,
                    ),
            )
        }
        val emoticon = Emoticon(commentId = commentId, userId = userId, emoji = emoji)
        val saved = emoticonRepository.save(emoticon)
        // save 가 이미 CommentEntity 를 1차 캐시에 올려서 추가 SELECT 없음(F3).
        // actorUserId 는 반응한 사람(userId)이다 — 댓글 작성자(comment.userId)가 아니다.
        val comment = commentRepository.findById(commentId)
        eventPublisher.publishEvent(
            EmoticonAddedEvent(
                emoticonId = saved.id,
                commentId = commentId,
                photoId = comment.photoId,
                actorUserId = userId,
                emoji = emoji,
            ),
        )
        return saved
    }

    @Transactional
    override fun removeEmoticon(
        commentId: Long,
        userId: Long,
        emoji: String,
    ) {
        emoticonRepository.delete(commentId, userId, emoji)
    }
}
