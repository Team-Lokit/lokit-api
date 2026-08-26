package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import kr.co.lokit.api.common.concurrency.LockManager
import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.NotificationSettingsRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.PushSenderPort
import kr.co.lokit.api.domain.notification.application.port.findOrDefault
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationMessage
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.notification.domain.PushMessage
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * 클래스에 @Transactional 없음(D5) — DB 쓰기는 lockManager.withLock의 REQUIRES_NEW 트랜잭션과
 * 어댑터 메서드의 @Transactional 안에서만. 외부 HTTP는 항상 트랜잭션·락 밖.
 * 푸시 발송 조건은 두 단계다: 전역 flag(pushSenderPort 존재) → 수신자별 알림 설정(종류별 스위치).
 * 둘 다 sendPush 한 곳에서만 검사한다(공개 진입점 3개가 모두 이 private 을 경유).
 */
@Service
class NotificationDispatchService(
    private val notificationRepository: NotificationRepositoryPort,
    private val deviceTokenRepository: DeviceTokenRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val appEventLogPort: AppEventLogPort,
    private val lockManager: LockManager,
    private val notificationSettingsRepository: NotificationSettingsRepositoryPort,
    private val pushSenderPort: PushSenderPort?,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 열린 윈도우 있으면 count만 증가하고 null 반환(푸시 없음). 없으면 신규 저장+즉시발송 후 반환. */
    fun notifyPhotoInteraction(
        recipientUserId: Long,
        actorUserId: Long,
        targetPhotoId: Long,
        notificationType: NotificationType,
        now: LocalDateTime = LocalDateTime.now(),
    ): Notification? {
        val created =
            lockManager.withLock(
                key = Notification.groupWindowLockKey(recipientUserId, targetPhotoId),
                operation = {
                    val openWindow =
                        notificationRepository
                            .findLatestUnclosedByRecipientAndPhoto(recipientUserId, targetPhotoId)
                            ?.takeIf { it.isGroupWindowOpen(now) }
                    if (openWindow != null) {
                        notificationRepository.increaseGroupCount(openWindow.id)
                        null
                    } else {
                        notificationRepository.save(
                            newNotification(recipientUserId, actorUserId, targetPhotoId, notificationType, now),
                        )
                    }
                },
            )
        created?.let { sendPush(it) }
        return created
    }

    /** 마감 배치 진입점. group_count>1이면 후속 1건 추가 발송. ==1이면 마감표시만(폴링 제외 목적). */
    fun closeGroupWindow(
        notification: Notification,
        now: LocalDateTime = LocalDateTime.now(),
    ): Notification {
        val summaryBody =
            if (notification.isGroupSummaryRequired()) {
                NotificationMessage.body(
                    resolveActorName(notification.actorUserId),
                    notification.notificationType,
                    notification.groupCount,
                )
            } else {
                notification.body
            }
        val closed = notificationRepository.closeGroupWindow(notification.id, now, summaryBody)
        if (closed.isGroupSummaryRequired()) {
            sendPush(closed)
        }
        return closed
    }

    /** 완성된 알림 1건 저장+즉시발송. 그룹 윈도우 개념 없음(D7). 기존 private sendPush 재사용. */
    fun dispatchImmediately(notification: Notification): Notification {
        val saved = notificationRepository.save(notification)
        sendPush(saved)
        return saved
    }

    private fun newNotification(
        recipientUserId: Long,
        actorUserId: Long,
        targetPhotoId: Long,
        notificationType: NotificationType,
        now: LocalDateTime,
    ): Notification =
        Notification(
            notifId = UUID.randomUUID().toString(),
            recipientUserId = recipientUserId,
            actorUserId = actorUserId,
            notificationType = notificationType,
            targetPhotoId = targetPhotoId,
            groupCount = Notification.MIN_GROUP_COUNT,
            title = NotificationMessage.title(notificationType),
            body =
                NotificationMessage.body(
                    resolveActorName(actorUserId),
                    notificationType,
                    Notification.MIN_GROUP_COUNT,
                ),
            sentAt = now,
        )

    private fun resolveActorName(actorUserId: Long): String =
        userRepository.findById(actorUserId)?.name ?: NotificationMessage.DEFAULT_ACTOR_NAME

    private fun sendPush(notification: Notification) {
        val sender =
            pushSenderPort ?: run {
                log.debug("푸시 비활성화 상태 — 알림함만 저장: notifId={}", notification.notifId)
                return
            }
        val settings = notificationSettingsRepository.findOrDefault(notification.recipientUserId)
        if (!settings.isPushEnabledFor(notification.notificationType)) {
            log.debug(
                "수신자 알림 설정 OFF — 알림함만 저장: notifId={}, type={}",
                notification.notifId,
                notification.notificationType,
            )
            return
        }
        val tokens = deviceTokenRepository.findAllByUserId(notification.recipientUserId).map { it.token }
        if (tokens.isEmpty()) {
            log.debug("등록된 디바이스 토큰 없음 — 발송 생략: notifId={}", notification.notifId)
            return
        }
        val result =
            sender.send(
                PushMessage(
                    tokens = tokens,
                    title = notification.title,
                    body = notification.body,
                    data = notification.dataPayload(),
                ),
            )
        appEventLogPort.record(
            eventName = PUSH_SEND_EVENT,
            userId = notification.recipientUserId,
            notifId = notification.notifId,
            notifType = notification.notificationType.name,
            params =
                mapOf(
                    "group_count" to notification.groupCount,
                    "success_count" to result.successCount,
                    "failure_count" to result.failureCount,
                    "invalid_count" to result.invalidTokens.size,
                ),
        )
        if (result.invalidTokens.isNotEmpty()) {
            log.info("무효 FCM 토큰 감지: count={}, notifId={}", result.invalidTokens.size, notification.notifId)
        }
    }

    companion object {
        const val PUSH_SEND_EVENT: String = "push_send"
    }
}
