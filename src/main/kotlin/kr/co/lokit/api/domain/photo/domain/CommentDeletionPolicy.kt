package kr.co.lokit.api.domain.photo.domain

object CommentDeletionPolicy {
    fun shouldPlaceholderDelete(
        isReply: Boolean,
        hasReplies: Boolean,
    ): Boolean = !isReply && hasReplies
}
