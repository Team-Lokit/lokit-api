package kr.co.lokit.api.domain.photo.application.port

import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons

interface CommentRepositoryPort {
    fun save(comment: Comment): Comment

    fun findById(id: Long): Comment

    fun findAllByPhotoIdWithEmoticons(
        photoId: Long,
        currentUserId: Long,
    ): List<CommentWithEmoticons>

    fun findIdsByUserIdAndCoupleIds(
        userId: Long,
        coupleIds: Set<Long>,
    ): Set<Long>

    fun findIdsByUserIdAndPhotoIds(
        userId: Long,
        photoIds: Set<Long>,
    ): Set<Long>

    fun countRepliesByParentId(commentId: Long): Long

    fun update(
        id: Long,
        content: String,
    ): Comment

    fun markRemoved(
        id: Long,
        placeholder: String,
    ): Comment

    fun deleteHard(id: Long)
}
