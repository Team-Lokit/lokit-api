package kr.co.lokit.api.domain.notification.domain

/** 제목은 최초 이벤트 종류로 고정. 그룹 요약도 타입별로 갈린다(버그픽스 — 과거엔 "댓글"로 고정돼 있었다). */
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

    /**
     * emoji는 단건(그룹 아님) REACTION 본문에만 쓰인다. 그룹으로 쌓이면 서로 다른 이모지가
     * 섞일 수 있어 그룹 요약엔 이모지를 보여주지 않는다(화면 기획도 "반응 N개"만 보여준다) —
     * 그래서 groupCount>1 분기는 emoji를 아예 참조하지 않는다.
     * UPLOAD 분기는 N-1 전용인 이 함수의 현재 호출부 어디서도 도달하지 않는다 — exhaustive
     * `when`을 위한 방어적 항목이다(N-2는 uploadBody()를 쓴다).
     */
    fun body(
        actorName: String,
        notificationType: NotificationType,
        groupCount: Int,
        emoji: String? = null,
    ): String {
        val name = actorName.ifBlank { DEFAULT_ACTOR_NAME }
        return if (groupCount > Notification.MIN_GROUP_COUNT) {
            when (notificationType) {
                NotificationType.COMMENT -> "${name}님이 댓글 ${groupCount}개를 남겼어요"
                NotificationType.REACTION -> "${name}님이 반응 ${groupCount}개를 남겼어요"
                NotificationType.UPLOAD -> "${name}님이 사진 ${groupCount}장을 남겼어요"
            }
        } else {
            when (notificationType) {
                NotificationType.COMMENT -> "${name}님이 댓글을 남겼어요"
                NotificationType.REACTION ->
                    if (emoji != null) "${name}님이 ${emoji} 반응을 남겼어요" else "${name}님이 반응을 남겼어요"
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
