package kr.co.lokit.api.domain.notification.domain

import java.time.LocalDateTime

/**
 * 업로드 알림(N-2) 대기 배치. 사진 업로드마다 타이머를 10분 뒤로 리셋한다.
 * 앵커는 가변 마감기한 scheduledAt이다 — N-1(Notification)의 불변 창 시작 sentAt과 방향이 반대다.
 * 확장 규칙: 컬럼 추가는 nullable 또는 기본값 있는 non-null로만 (마이그레이션 도구 없음, prod ddl-auto=validate).
 */
data class PendingUploadNotification(
    val id: Long = 0L,
    val coupleId: Long,
    val recipientUserId: Long,
    val actorUserId: Long,
    val photoIds: List<Long>,
    val scheduledAt: LocalDateTime,
    val sentAt: LocalDateTime? = null,
) {
    init {
        require(photoIds.isNotEmpty()) { "대상 사진이 최소 1장 있어야 합니다." }
        require(photoIds.size <= MAX_PHOTO_IDS) { "대상 사진은 ${MAX_PHOTO_IDS}장을 넘을 수 없습니다." }
        require(recipientUserId != actorUserId) { "자기 자신에게는 알림을 예약할 수 없습니다." }
    }

    fun isSent(): Boolean = sentAt != null

    /** 경계: now == scheduledAt이면 '발송 대상'이다(N-1 isGroupWindowOpen과 부호 반대). */
    fun isDue(now: LocalDateTime): Boolean = sentAt == null && !now.isBefore(scheduledAt)

    /** 타이머 리셋: scheduledAt을 now+10분으로 재대입한다. distinct()가 takeLast()보다 먼저 와야 한다. */
    fun withPhoto(photoId: Long, now: LocalDateTime): PendingUploadNotification =
        copy(
            photoIds = (photoIds + photoId).distinct().takeLast(MAX_PHOTO_IDS),
            scheduledAt = scheduleFrom(now),
        )

    fun photoCount(): Int = photoIds.size

    fun representativePhotoId(): Long = photoIds.last()

    companion object {
        const val DEBOUNCE_MINUTES: Long = 10
        const val MAX_PHOTO_IDS: Int = 200
        const val PHOTO_IDS_COLUMN_LENGTH: Int = 2000
        private const val DELIMITER: String = ","
        private const val LOCK_PREFIX: String = "notification:upload:"

        fun scheduleFrom(now: LocalDateTime): LocalDateTime = now.plusMinutes(DEBOUNCE_MINUTES)

        fun lockKey(coupleId: Long, actorUserId: Long): String = "$LOCK_PREFIX$coupleId:$actorUserId"

        fun encodePhotoIds(ids: List<Long>): String = ids.joinToString(DELIMITER)

        fun decodePhotoIds(raw: String): List<Long> =
            raw.split(DELIMITER).filter { it.isNotBlank() }.map { it.trim().toLong() }

        /** '동일 장소' = 리버스 지오코딩 주소 문자열 동일 여부. 하나라도 다르거나 널이 섞이면 null. */
        fun commonAddressOf(addresses: List<String?>): String? = addresses.distinct().singleOrNull()

        fun newBatch(
            coupleId: Long,
            recipientUserId: Long,
            actorUserId: Long,
            photoId: Long,
            now: LocalDateTime,
        ): PendingUploadNotification =
            PendingUploadNotification(
                coupleId = coupleId,
                recipientUserId = recipientUserId,
                actorUserId = actorUserId,
                photoIds = listOf(photoId),
                scheduledAt = scheduleFrom(now),
            )
    }
}
