package kr.co.lokit.api.domain.notification.domain

/** 제목은 최초 이벤트 종류로 고정. 그룹 요약은 타입 구분 없이 통합 카운트. */
object NotificationMessage {
    const val DEFAULT_ACTOR_NAME: String = "상대방"
    const val MAX_TITLE_LENGTH: Int = 100
    const val MAX_BODY_LENGTH: Int = 200

    fun title(notificationType: NotificationType): String =
        when (notificationType) {
            NotificationType.COMMENT -> "새 댓글"
            NotificationType.REACTION -> "새 반응"
            NotificationType.UPLOAD -> "새 사진"
        }

    fun body(
        actorName: String,
        notificationType: NotificationType,
        groupCount: Int,
    ): String {
        val name = actorName.ifBlank { DEFAULT_ACTOR_NAME }
        return if (groupCount > Notification.MIN_GROUP_COUNT) {
            "${name}님이 댓글 ${groupCount}개를 남겼어요"
        } else {
            when (notificationType) {
                NotificationType.COMMENT -> "${name}님이 댓글을 남겼어요"
                NotificationType.REACTION -> "${name}님이 반응을 남겼어요"
                NotificationType.UPLOAD -> "${name}님이 사진을 올렸어요"
            }
        }
    }

    /** N-2 전용. 기존 body()의 groupCount>1 분기는 "댓글" 하드코딩된 N-1 특유 결함이라 재사용하지 않는다. */
    fun uploadBody(
        actorName: String,
        photoCount: Int,
        address: String?,
    ): String {
        val name = actorName.ifBlank { DEFAULT_ACTOR_NAME }
        val raw =
            when {
                address == null -> "${name}님이 새로운 추억 ${photoCount}장을 남겼어요"
                photoCount <= Notification.MIN_GROUP_COUNT -> "${name}님이 ${address}에 새로운 추억을 남겼어요"
                else -> "${name}님이 ${address}에 사진 ${photoCount}장을 올렸어요"
            }
        return raw.take(MAX_BODY_LENGTH)
    }
}
