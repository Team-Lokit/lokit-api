package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * @ConditionalOnProperty로 게이팅하지 않는다 — NotificationGroupWindowScheduler:12-15와 같은 근거다.
 * 플래그가 꺼진 동안 정리되지 않은 행이 무한히 쌓인다.
 * cron 슬롯 06:00은 기존 5개 배치(매분×2, 03/04/05/05:30)와 겹치지 않는다.
 */
@Component
class NotificationRetentionScheduler(
    private val notificationRepository: NotificationRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 6 * * *")
    fun purgeExpiredNotifications() {
        val cutoff = Notification.retentionCutoff(LocalDateTime.now())
        var total = 0
        var round = 0
        while (round < MAX_BATCHES) {
            val deleted =
                try {
                    notificationRepository.deleteSentBefore(cutoff, BATCH_LIMIT)
                } catch (e: Exception) {
                    log.warn("알림 정리 실패 (다음 주기에 재시도): cutoff={}, 이번 회차까지 {}건", cutoff, total, e)
                    break
                }
            total += deleted
            if (deleted < BATCH_LIMIT) break
            round++
        }
        if (total > 0) {
            log.info("30일 경과 알림 정리: {}건", total)
        }
    }

    companion object {
        const val BATCH_LIMIT: Int = 500
        const val MAX_BATCHES: Int = 20 // 1회 실행 상한 10,000건
    }
}
