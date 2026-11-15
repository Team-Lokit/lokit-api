package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.UploadNotificationService
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 🔴 scheduledAtBefore 에 now 를 그대로 넘긴다(D3/B11).
 * 산술은 이미 쓰기측(scheduledAt = now + 10분)에서 끝났다 — 슬라이스3 스케줄러를 복사해
 * minusMinutes(10) 을 넣으면 실제 발송이 T+20분이 된다.
 */
@Component
class PendingUploadNotificationScheduler(
    private val pendingRepository: PendingUploadNotificationRepositoryPort,
    private val uploadNotificationService: UploadNotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 * * * * *")
    fun firePendingUploadNotifications() {
        val now = LocalDateTime.now()
        val targets =
            pendingRepository.findDuePendings(
                scheduledAtBefore = now,
                limit = BATCH_LIMIT,
            )
        if (targets.isEmpty()) {
            return
        }
        targets.forEach { pending ->
            try {
                uploadNotificationService.fire(pending, now)
            } catch (e: Exception) {
                // fire 는 claim(markSent) 단계 실패만 던진다 — 아무것도 커밋되지 않았으므로 다음 주기에 그대로 재시도된다.
                // claim 이후 실패는 fire 내부에서 재시도·error 로그로 처리된다.
                log.warn("업로드 알림 claim 실패 (커밋 안 됨, 다음 주기에 재시도): pendingId={}", pending.id, e)
            }
        }
        log.info("업로드 알림 배치 처리: {}건", targets.size)
    }

    companion object {
        const val BATCH_LIMIT: Int = 200
    }
}
