package kr.co.lokit.api.domain.photo.domain

/** 규약: 두 이벤트 모두 photoId와 actorUserId를 반드시 채운다. photo 도메인은 NotificationType을 모른다(D4). */
data class CommentCreatedEvent(
    val commentId: Long,
    val photoId: Long,
    val actorUserId: Long,
)

data class EmoticonAddedEvent(
    val emoticonId: Long,
    val commentId: Long,
    val photoId: Long,
    val actorUserId: Long,
    val emoji: String,
)
