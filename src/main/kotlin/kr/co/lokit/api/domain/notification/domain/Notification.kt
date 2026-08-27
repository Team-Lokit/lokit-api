package kr.co.lokit.api.domain.notification.domain

import java.time.LocalDateTime

/**
 * 알림함 row 겸 그룹 윈도우 앵커.
 * sentAt이 창의 시작 시각을 겸한다. 창은 첫 이벤트 기준 고정 5분, 연장 없음.
 * groupClosedAt==null은 "마감 배치가 아직 처리하지 않음"을 뜻한다 — "지금 열려 있음"과 다른 말이다.
 * 확장 규칙: 컬럼 추가는 nullable 또는 기본값 있는 non-null로만 (마이그레이션 도구 없음, prod ddl-auto=validate).
 */
data class Notification(
    val id: Long = 0L,
    val notifId: String,
    val recipientUserId: Long,
    val actorUserId: Long,
    val notificationType: NotificationType,
    val targetPhotoId: Long,
    val groupCount: Int = 1,
    val title: String,
    val body: String,
    val isRead: Boolean = false,
    val sentAt: LocalDateTime,
    val groupClosedAt: LocalDateTime? = null,
    /** 딥링크 힌트. non-null=단일 장소, null=여러 장소 또는 N-1 알림(지도홈). */
    val targetAddress: String? = null,
) {
    init {
        require(notifId.isNotBlank()) { "알림 식별자는 필수입니다." }
        require(notifId.length <= NOTIF_ID_LENGTH) { "알림 식별자는 ${NOTIF_ID_LENGTH}자 이내여야 합니다." }
        require(groupCount >= MIN_GROUP_COUNT) { "그룹 개수는 ${MIN_GROUP_COUNT} 이상이어야 합니다." }
        require(title.isNotBlank()) { "알림 제목은 필수입니다." }
        require(body.isNotBlank()) { "알림 본문은 필수입니다." }
    }

    /** 경계 의미: now == sentAt + 5분 이면 '닫힘'이다. */
    fun isGroupWindowOpen(now: LocalDateTime): Boolean =
        groupClosedAt == null && now.isBefore(sentAt.plusMinutes(GROUP_WINDOW_MINUTES))

    /** 마감 시점에 후속 푸시 1건을 더 보내야 하는가. */
    fun isGroupSummaryRequired(): Boolean = groupCount > MIN_GROUP_COUNT

    /**
     * FCM data payload. 값은 전부 문자열(FCM v1 제약). 키는 클라이언트 소비용 camelCase.
     * targetAddress는 non-null일 때만 넣는다 — 무조건 넣으면 기존 N-1 페이로드가 바뀐다.
     */
    fun dataPayload(): Map<String, String> =
        buildMap {
            put("notifId", notifId)
            put("notifType", notificationType.name)
            put("photoId", targetPhotoId.toString())
            put("groupCount", groupCount.toString())
            targetAddress?.let { put("targetAddress", it) }
        }

    companion object {
        const val GROUP_WINDOW_MINUTES: Long = 5
        const val NOTIF_ID_LENGTH: Int = 36
        const val MIN_GROUP_COUNT: Int = 1
        const val TARGET_ADDRESS_LENGTH: Int = 255

        /** 알림함 보존 기간. 이보다 오래된 알림은 정리 배치가 소프트삭제한다. */
        const val RETENTION_DAYS: Long = 30
        private const val GROUP_WINDOW_LOCK_PREFIX = "notification:group:"

        /** 타입까지 키에 넣는다 — 그렇지 않으면 댓글 윈도우가 열려 있을 때 반응이 거기 합쳐진다(버그3). */
        fun groupWindowLockKey(recipientUserId: Long, targetPhotoId: Long, notificationType: NotificationType): String =
            "$GROUP_WINDOW_LOCK_PREFIX$recipientUserId:$targetPhotoId:${notificationType.name}"

        fun closableWindowCutoff(now: LocalDateTime): LocalDateTime = now.minusMinutes(GROUP_WINDOW_MINUTES)

        /** 이 시각 '이전'(<, 경계 미포함)에 발송된 알림이 정리 대상이다. closableWindowCutoff 와 같은 모양. */
        fun retentionCutoff(now: LocalDateTime): LocalDateTime = now.minusDays(RETENTION_DAYS)

        /**
         * 업로드 알림 팩터리. groupClosedAt을 sentAt으로 항상 채운다.
         * null로 두면 마감 배치가 5분 뒤 이 알림을 집어 본문을 NotificationMessage.body()의 그룹
         * 요약 문구로 덮어쓰고 2차 푸시를 보낸다 — uploadBody()가 만든 "장소 O에 사진 N장을
         * 올렸어요" 같은 본문을 잃게 되므로 반드시 sentAt으로 채워 마감 배치 대상에서 제외한다.
         */
        fun upload(
            notifId: String,
            recipientUserId: Long,
            actorUserId: Long,
            targetPhotoId: Long,
            targetAddress: String?,
            photoCount: Int,
            title: String,
            body: String,
            sentAt: LocalDateTime,
        ): Notification =
            Notification(
                notifId = notifId,
                recipientUserId = recipientUserId,
                actorUserId = actorUserId,
                notificationType = NotificationType.UPLOAD,
                targetPhotoId = targetPhotoId,
                groupCount = photoCount,
                title = title,
                body = body,
                sentAt = sentAt,
                groupClosedAt = sentAt,
                targetAddress = targetAddress,
            )
    }
}
