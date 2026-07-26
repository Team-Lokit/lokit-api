package kr.co.lokit.api.domain.photo.infrastructure

import kr.co.lokit.api.common.exception.entityNotFound
import kr.co.lokit.api.domain.photo.application.port.CommentRepositoryPort
import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.domain.DeIdentifiedUserProfile
import kr.co.lokit.api.domain.photo.domain.EmoticonSummary
import kr.co.lokit.api.domain.photo.domain.Photo
import kr.co.lokit.api.domain.photo.infrastructure.mapping.toDomain
import kr.co.lokit.api.domain.photo.infrastructure.mapping.toEntity
import kr.co.lokit.api.domain.user.domain.User
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class JpaCommentRepository(
    private val commentJpaRepository: CommentJpaRepository,
    private val emoticonJpaRepository: EmoticonJpaRepository,
    private val photoJpaRepository: PhotoJpaRepository,
    private val userJpaRepository: UserJpaRepository,
) : CommentRepositoryPort {
    override fun save(comment: Comment): Comment {
        val photoEntity =
            photoJpaRepository.findByIdOrNull(comment.photoId)
                ?: throw entityNotFound<Photo>(comment.photoId)
        val userEntity =
            userJpaRepository.findByIdOrNull(comment.userId)
                ?: throw entityNotFound<User>(comment.userId)
        val parentEntity =
            comment.parentId?.let { parentId ->
                commentJpaRepository.findByIdOrNull(parentId)
                    ?: throw entityNotFound<Comment>(parentId)
            }
        val entity = comment.toEntity(photoEntity, userEntity, parentEntity)
        return commentJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Comment {
        val entity =
            commentJpaRepository.findByIdOrNull(id)
                ?: throw entityNotFound<Comment>(id)
        return entity.toDomain()
    }

    override fun findAllByPhotoIdWithEmoticons(
        photoId: Long,
        currentUserId: Long,
    ): List<CommentWithEmoticons> {
        val comments = commentJpaRepository.findAllByPhotoId(photoId)
        if (comments.isEmpty()) return emptyList()

        val emoticons = emoticonJpaRepository.findAllByCommentIn(comments)
        val emoticonsByComment = emoticons.groupBy { it.comment.nonNullId() }

        fun toReadModel(entity: CommentEntity): CommentWithEmoticons {
            val commentEmoticons = emoticonsByComment[entity.nonNullId()].orEmpty()
            val summaries =
                commentEmoticons
                    .groupBy { it.emoji }
                    .map { (emoji, group) ->
                        EmoticonSummary(
                            emoji = emoji,
                            count = group.size,
                            reacted = group.any { it.userId == currentUserId },
                        )
                    }
            return CommentWithEmoticons(
                comment = entity.toDomain(),
                userName = entity.user?.name ?: DeIdentifiedUserProfile.DISPLAY_NAME,
                userProfileImageUrl = entity.user?.profileImageUrl ?: DeIdentifiedUserProfile.hiddenProfileImageUrl(),
                emoticons = summaries,
            )
        }

        val (topLevel, replyEntities) = comments.partition { it.parent == null }
        val repliesByParentId = replyEntities.groupBy { it.parent!!.nonNullId() }

        return topLevel.map { top ->
            val replies = repliesByParentId[top.nonNullId()].orEmpty().map(::toReadModel)
            toReadModel(top).copy(replies = replies)
        }
    }

    override fun findIdsByUserIdAndCoupleIds(
        userId: Long,
        coupleIds: Set<Long>,
    ): Set<Long> {
        if (coupleIds.isEmpty()) return emptySet()
        return commentJpaRepository.findIdsByUserIdAndCoupleIds(userId, coupleIds).toSet()
    }

    override fun findIdsByUserIdAndPhotoIds(
        userId: Long,
        photoIds: Set<Long>,
    ): Set<Long> {
        if (photoIds.isEmpty()) return emptySet()
        return commentJpaRepository.findIdsByUserIdAndPhotoIds(userId, photoIds).toSet()
    }

    override fun countRepliesByParentId(commentId: Long): Long = commentJpaRepository.countByParentId(commentId)

    override fun update(
        id: Long,
        content: String,
    ): Comment {
        val entity =
            commentJpaRepository.findByIdOrNull(id)
                ?: throw entityNotFound<Comment>(id)
        entity.content = content
        return entity.toDomain()
    }

    override fun markRemoved(
        id: Long,
        placeholder: String,
    ): Comment {
        val entity =
            commentJpaRepository.findByIdOrNull(id)
                ?: throw entityNotFound<Comment>(id)
        entity.content = placeholder
        entity.removed = true
        return entity.toDomain()
    }

    override fun deleteHard(id: Long) {
        commentJpaRepository.deleteById(id)
    }
}
