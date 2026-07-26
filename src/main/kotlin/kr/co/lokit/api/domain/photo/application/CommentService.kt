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
import kr.co.lokit.api.domain.photo.domain.CommentDeletionPolicy
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.domain.Emoticon
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val commentRepository: CommentRepositoryPort,
    private val emoticonRepository: EmoticonRepositoryPort,
    private val coupleRepository: CoupleRepositoryPort,
) : CommentUseCase,
    EmoticonUseCase {
    @Transactional
    override fun createComment(
        photoId: Long,
        userId: Long,
        content: String,
    ): Comment {
        val comment = Comment(photoId = photoId, userId = userId, content = content)
        return commentRepository.save(comment)
    }

    @Transactional(readOnly = true)
    override fun getComments(
        photoId: Long,
        currentUserId: Long,
    ): List<CommentWithEmoticons> {
        val comments = commentRepository.findAllByPhotoIdWithEmoticons(photoId, currentUserId)
        val deIdentifyUserId = coupleRepository.findByUserId(currentUserId)?.deIdentifiedUserId()
            ?: return comments

        fun deIdentifyIfNeeded(comment: CommentWithEmoticons): CommentWithEmoticons {
            val self = if (comment.comment.userId == deIdentifyUserId) comment.deIdentified() else comment
            return self.copy(replies = self.replies.map(::deIdentifyIfNeeded))
        }
        return comments.map(::deIdentifyIfNeeded)
    }

    @Transactional
    override fun createReply(
        parentCommentId: Long,
        userId: Long,
        content: String,
    ): Comment {
        val parent = commentRepository.findById(parentCommentId)
        if (parent.removed) {
            throw BusinessException.ReplyToRemovedNotAllowedException(
                errors = errorDetailsOf(ErrorField.PARENT_COMMENT_ID to parentCommentId),
            )
        }
        if (parent.isReply()) {
            throw BusinessException.ReplyDepthExceededException(
                errors = errorDetailsOf(ErrorField.PARENT_COMMENT_ID to parentCommentId),
            )
        }
        val reply =
            Comment(
                photoId = parent.photoId,
                userId = userId,
                parentId = parent.id,
                content = content,
            )
        return commentRepository.save(reply)
    }

    @Transactional
    override fun updateComment(
        commentId: Long,
        userId: Long,
        content: String,
    ): Comment {
        val existing = commentRepository.findById(commentId)
        if (existing.removed) {
            throw BusinessException.CommentAlreadyRemovedException(
                errors = errorDetailsOf(ErrorField.COMMENT_ID to commentId),
            )
        }
        return commentRepository.update(commentId, content)
    }

    @Transactional
    override fun deleteComment(
        commentId: Long,
        userId: Long,
    ) {
        val existing = commentRepository.findById(commentId)
        val isReply = existing.isReply()
        val hasReplies = !isReply && commentRepository.countRepliesByParentId(commentId) > 0
        if (CommentDeletionPolicy.shouldPlaceholderDelete(isReply, hasReplies)) {
            commentRepository.markRemoved(commentId, Comment.REMOVED_PLACEHOLDER_TEXT)
        } else {
            commentRepository.deleteHard(commentId)
        }
    }

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
        val count = emoticonRepository.countByCommentIdAndUserId(commentId, userId)
        if (!Emoticon.canAdd(count)) {
            throw BusinessException.CommentMaxEmoticonsExceededException(
                errors =
                    errorDetailsOf(
                        ErrorField.COMMENT_ID to commentId,
                        ErrorField.USER_ID to userId,
                    ),
            )
        }
        val emoticon = Emoticon(commentId = commentId, userId = userId, emoji = emoji)
        return emoticonRepository.save(emoticon)
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
