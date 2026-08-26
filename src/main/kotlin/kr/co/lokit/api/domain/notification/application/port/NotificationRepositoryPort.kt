package kr.co.lokit.api.domain.notification.application.port

import kr.co.lokit.api.domain.notification.domain.Notification
import java.time.LocalDateTime

/** 포트는 '5분' 정책을 모른다(D2). 열림 판정은 Notification.isGroupWindowOpen이 한다. */
interface NotificationRepositoryPort {
    fun save(notification: Notification): Notification

    /** group_closed_at is null인 것 중 sent_at이 가장 최근인 1건. 열림 판정은 호출자 몫. */
    fun findLatestUnclosedByRecipientAndPhoto(recipientUserId: Long, targetPhotoId: Long): Notification?

    /** group_count += 1 (더티체킹). 대상 없으면 entityNotFound. */
    fun increaseGroupCount(notificationId: Long): Notification

    /** group_closed_at is null and sent_at <= sentAtBefore, sent_at 오름차순, 최대 limit건. */
    fun findClosableGroupWindows(sentAtBefore: LocalDateTime, limit: Int): List<Notification>

    /** 마감 표시 + 본문 갱신. group_count==1이면 body를 그대로 넘긴다. */
    fun closeGroupWindow(notificationId: Long, closedAt: LocalDateTime, body: String): Notification
}
