package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.NotificationDispatchService
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * @ConditionalOnProperty로 게이팅하지 않는다(D2) — 마감은 부기이고 실제 발송은 PushSenderPort?가
 * null이면 자동 생략된다. 게이팅하면 플래그 꺼진 동안 미마감 행이 무한히 쌓여 나중에 폭발한다.
 * cron(매분 0초) 사용 — fixedDelay는 기동 직후 즉시 발화, @EnableScheduling 전역이라
 * @SpringBootTest에서도 등록되므로 발화 회피.
 */
@Component
class NotificationGroupWindowScheduler(
    private val notificationRepository: NotificationRepositoryPort,
    private val notificationDispatchService: NotificationDispatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *")
    fun closeExpiredGroupWindows() {
        val now = LocalDateTime.now()
        val targets =
            notificationRepository.findClosableGroupWindows(
                sentAtBefore = Notification.closableWindowCutoff(now),
                limit = BATCH_LIMIT,
            )
        if (targets.isEmpty()) {
            return
        }
        targets.forEach { notification ->
            try {
                notificationDispatchService.closeGroupWindow(notification, now)
            } catch (e: Exception) {
                log.warn("그룹 윈도우 마감 실패 (다음 주기에 재시도): notifId={}", notification.notifId, e)
            }
        }
        log.info("그룹 윈도우 마감 처리: {}건", targets.size)
    }

    companion object {
        const val BATCH_LIMIT: Int = 200
    }
}
