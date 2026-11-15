package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import kr.co.lokit.api.common.annotation.OptimisticRetry
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
import kr.co.lokit.api.domain.notification.domain.PushSendResult
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
 *
 * 예외 정책:
 * - DB 상태 변경(저장·카운트 증가·마감)은 락 안 트랜잭션에서 커밋까지 끝낸다. 다른 인스턴스와의
 *   @Version 충돌은 @OptimisticRetry 가 메서드 전체를 다시 실행한다(재조회부터 다시 하므로 안전).
 * - sendPush 는 절대 예외를 던지지 않는다. 알림함 행은 이미 커밋됐으므로, 푸시 단계 실패가
 *   호출자로 전파되면 호출자가 "실패"로 오인해 재시도/중복을 만든다. 실패는 error 로그로 남긴다.
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
    private val coupleConnectionChecker: CoupleConnectionChecker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 열린 윈도우 있으면 count만 증가하고 null 반환(푸시 없음). 없으면 신규 저장+즉시발송 후 반환.
     * emoji는 REACTION 신규 생성 시 본문에만 쓰인다(그룹 합류 시엔 무시 — 그룹 요약은 이모지를
     * 보여주지 않는다). 락 키·조회 모두 notificationType으로 걸러 댓글/반응이 서로 섞이지 않는다(버그3).
     */
    @OptimisticRetry
    fun notifyPhotoInteraction(
        recipientUserId: Long,
        actorUserId: Long,
        targetPhotoId: Long,
        notificationType: NotificationType,
        emoji: String? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): Notification? {
        val created =
            lockManager.withLock(
                key = Notification.groupWindowLockKey(recipientUserId, targetPhotoId, notificationType),
                operation = {
                    val openWindow =
                        notificationRepository
                            .findLatestUnclosedByRecipientAndPhoto(recipientUserId, targetPhotoId, notificationType)
                            ?.takeIf { it.isGroupWindowOpen(now) }
                    if (openWindow != null) {
                        notificationRepository.increaseGroupCount(openWindow.id)
                        null
                    } else {
                        notificationRepository.save(
                            newNotification(recipientUserId, actorUserId, targetPhotoId, notificationType, emoji, now),
                        )
                    }
                },
            )
        created?.let { sendPush(it) }
        return created
    }

    /**
     * 마감 배치 진입점. group_count>1이면 후속 1건 추가 발송. ==1이면 마감표시만(폴링 제외 목적).
     *
     * notifyPhotoInteraction 과 같은 락 키로 직렬화하고, 락 안에서 최신 행을 다시 읽는다.
     * 스케줄러가 넘긴 notification 은 조회 시점 스냅샷이라 그 사이 늘어난 group_count 를 모른다 —
     * 스냅샷으로 본문을 만들면 카운트가 틀리거나(1→2 경합 시) 요약 푸시 자체가 빠진다.
     */
    @OptimisticRetry
    fun closeGroupWindow(
        notification: Notification,
        now: LocalDateTime = LocalDateTime.now(),
    ): Notification {
        val closed =
            lockManager.withLock(
                key =
                    Notification.groupWindowLockKey(
                        notification.recipientUserId,
                        notification.targetPhotoId,
                        notification.notificationType,
                    ),
                operation = {
                    val fresh = notificationRepository.findByNotifId(notification.notifId)
                    when {
                        fresh == null -> {
                            log.info("마감 대상 알림이 이미 삭제됨 — 건너뜀: notifId={}", notification.notifId)
                            null
                        }
                        fresh.groupClosedAt != null -> {
                            log.debug("이미 마감된 알림 — 건너뜀: notifId={}", notification.notifId)
                            null
                        }
                        else -> notificationRepository.closeGroupWindow(fresh.id, now, summaryBodyOf(fresh))
                    }
                },
            ) ?: return notification
        if (closed.isGroupSummaryRequired()) {
            sendSummaryPushIfStillConnected(closed)
        }
        return closed
    }

    /** 마감은 부기라 그대로 하되, 그 사이 연결이 끊겼으면 요약 푸시만 생략한다. */
    private fun sendSummaryPushIfStillConnected(closed: Notification) {
        val connected =
            runCatching { coupleConnectionChecker.areConnectedPartners(closed.recipientUserId, closed.actorUserId) }
                .getOrElse { e ->
                    log.error("커플 연결 확인 실패 — 요약 푸시 생략: notifId={}", closed.notifId, e)
                    return
                }
        if (!connected) {
            log.info(
                "그 사이 커플 연결이 끊겨 요약 푸시 생략: notifId={}, recipientUserId={}, actorUserId={}",
                closed.notifId,
                closed.recipientUserId,
                closed.actorUserId,
            )
            return
        }
        sendPush(closed)
    }

    private fun summaryBodyOf(notification: Notification): String =
        if (notification.isGroupSummaryRequired()) {
            NotificationMessage.body(
                resolveActorName(notification.actorUserId),
                notification.notificationType,
                notification.groupCount,
            )
        } else {
            notification.body
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
        emoji: String?,
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
                    emoji,
                ),
            sentAt = now,
        )

    private fun resolveActorName(actorUserId: Long): String =
        userRepository.findById(actorUserId)?.name ?: NotificationMessage.DEFAULT_ACTOR_NAME

    /** 예외를 던지지 않는다(클래스 KDoc 예외 정책). 알림함 행은 이미 커밋된 상태다. */
    private fun sendPush(notification: Notification) {
        try {
            doSendPush(notification)
        } catch (e: Exception) {
            log.error(
                "푸시 발송 단계 실패 — 알림함에는 저장됨, 푸시 누락: notifId={}, type={}, recipientUserId={}",
                notification.notifId,
                notification.notificationType,
                notification.recipientUserId,
                e,
            )
        }
    }

    private fun doSendPush(notification: Notification) {
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
            log.info(
                "등록된 디바이스 토큰 없음 — 푸시 생략: notifId={}, recipientUserId={}",
                notification.notifId,
                notification.recipientUserId,
            )
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
        logSendResult(notification, tokens.size, result)
        recordSendEvent(notification, result)
        cleanUpInvalidTokens(result.invalidTokens, notification.notifId)
    }

    private fun logSendResult(
        notification: Notification,
        tokenCount: Int,
        result: PushSendResult,
    ) {
        when {
            result.successCount == 0 && result.failedTokens.isNotEmpty() ->
                log.error(
                    "푸시 전 기기 발송 실패: notifId={}, type={}, recipientUserId={}, tokens={}, failed={}, invalid={}",
                    notification.notifId,
                    notification.notificationType,
                    notification.recipientUserId,
                    tokenCount,
                    result.failedTokens.size,
                    result.invalidTokens.size,
                )
            result.failedTokens.isNotEmpty() ->
                log.warn(
                    "푸시 일부 기기 발송 실패: notifId={}, type={}, recipientUserId={}, success={}, failed={}, invalid={}",
                    notification.notifId,
                    notification.notificationType,
                    notification.recipientUserId,
                    result.successCount,
                    result.failedTokens.size,
                    result.invalidTokens.size,
                )
            else ->
                log.info(
                    "푸시 발송 완료: notifId={}, type={}, recipientUserId={}, success={}, invalid={}",
                    notification.notifId,
                    notification.notificationType,
                    notification.recipientUserId,
                    result.successCount,
                    result.invalidTokens.size,
                )
        }
    }

    /** 분석 이벤트 기록 실패가 무효 토큰 정리를 막지 않도록 분리한다. */
    private fun recordSendEvent(
        notification: Notification,
        result: PushSendResult,
    ) {
        runCatching {
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
        }.onFailure { e ->
            log.warn("푸시 발송 이벤트 기록 실패: notifId={}", notification.notifId, e)
        }
    }

    /** FCM 이 UNREGISTERED 로 판정한 토큰을 물리 삭제한다. 실패해도 예외를 던지지 않고 warn 로그만 남긴다. */
    private fun cleanUpInvalidTokens(
        invalidTokens: List<String>,
        notifId: String,
    ) {
        if (invalidTokens.isEmpty()) return
        runCatching { deviceTokenRepository.deleteAllByTokens(invalidTokens) }
            .onSuccess { deleted ->
                log.info(
                    "무효 FCM 토큰 삭제: detected={}, deleted={}, notifId={}",
                    invalidTokens.size,
                    deleted,
                    notifId,
                )
            }.onFailure { e ->
                log.warn(
                    "무효 FCM 토큰 삭제 실패: count={}, notifId={}",
                    invalidTokens.size,
                    notifId,
                    e,
                )
            }
    }

    companion object {
        const val PUSH_SEND_EVENT: String = "push_send"
    }
}
