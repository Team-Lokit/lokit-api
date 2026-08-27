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

    /**
     * 수신자 소유 알림을 sent_at 내림차순(동률 시 id 내림차순)으로 offset 페이지 조회.
     * 그룹 윈도우 마감 여부로 거르지 않는다(D2) — 쿼리에 group_closed_at 조건을 넣으면 안 된다.
     * page는 0-based, size >= 1을 전제한다(정규화는 호출자 몫).
     */
    fun findInboxPage(recipientUserId: Long, page: Int, size: Int): List<Notification>

    /** 수신자 소유 알림 총 개수. @SoftDelete로 정리된 행은 자동 제외된다. */
    fun countInbox(recipientUserId: Long): Long

    /** notifId(UUID)로 단건 조회. 없으면 null — 404/403 구분은 호출자 몫이다. */
    fun findByNotifId(notifId: String): Notification?

    /**
     * is_read = true (더티체킹, save 미호출).
     * 이미 true면 Hibernate 더티체크가 변화 없음을 보고 UPDATE를 내지 않는다(= @Version도 안 오른다).
     * 대상 없으면 entityNotFound.
     */
    fun markAsRead(notificationId: Long): Notification

    /**
     * sent_at < sentAtBefore인 알림을 오래된 순으로 최대 limit건 소프트삭제하고 실제 삭제 건수를 반환한다.
     * 경계: sent_at == sentAtBefore는 삭제하지 않는다(strictly before).
     */
    fun deleteSentBefore(sentAtBefore: LocalDateTime, limit: Int): Int
}
