package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 계약 §4 계층 F (R33~R37). 협력자가 리포지토리 포트 1개뿐이라
 * NotificationGroupWindowSchedulerTest 와 동일하게 @InjectMocks 로 조립한다.
 *
 * cutoff 는 스케줄러가 만드는 LocalDateTime.now() 파생값이라 고정할 수 없다.
 * 호출 전후 시각으로 구간을 만들어 캡처값이 그 안에 들어오는지로 검증한다
 * (기존 스케줄러 테스트와 같은 방식).
 */
@ExtendWith(MockitoExtension::class)
class NotificationRetentionSchedulerTest {
    @Mock
    lateinit var notificationRepository: NotificationRepositoryPort

    @InjectMocks
    lateinit var scheduler: NotificationRetentionScheduler

    /** R33 — 정리 기준 시각이 도메인 정책(RETENTION_DAYS=30)에서 나오고, 배치 크기가 계약값이다. */
    @Test
    fun `30일 전 컷오프로 정리를 요청한다`() {
        whenever(notificationRepository.deleteSentBefore(any(), any())).thenReturn(0)

        val beforeCall = LocalDateTime.now()
        scheduler.purgeExpiredNotifications()
        val afterCall = LocalDateTime.now()

        val cutoffCaptor = argumentCaptor<LocalDateTime>()
        val limitCaptor = argumentCaptor<Int>()
        verify(notificationRepository).deleteSentBefore(cutoffCaptor.capture(), limitCaptor.capture())

        val cutoff = cutoffCaptor.firstValue
        assertTrue(
            !cutoff.isBefore(beforeCall.minusDays(Notification.RETENTION_DAYS)) &&
                !cutoff.isAfter(afterCall.minusDays(Notification.RETENTION_DAYS)),
            "cutoff 는 호출 시점의 30일 전이어야 한다: cutoff=$cutoff, 호출구간=$beforeCall~$afterCall",
        )
        assertEquals(NotificationRetentionScheduler.BATCH_LIMIT, limitCaptor.firstValue)
        assertEquals(BATCH_LIMIT_EXPECTED, NotificationRetentionScheduler.BATCH_LIMIT)
    }

    /** R34 — 배치가 덜 찼다 = 더 지울 게 없다. 빈 삭제 쿼리를 한 번도 더 쏘지 않는다. */
    @Test
    fun `배치 크기보다 적게 지워지면 한 번만 호출한다`() {
        whenever(notificationRepository.deleteSentBefore(any(), any()))
            .thenReturn(NotificationRetentionScheduler.BATCH_LIMIT - 1)

        scheduler.purgeExpiredNotifications()

        verify(notificationRepository, times(1)).deleteSentBefore(any(), any())
    }

    /**
     * R35 ★ — 배치가 가득 차면 남은 대상이 있다는 뜻이므로 다음 배치를 이어서 돈다.
     * 500,500,200 → 정확히 3회. `repeat { ... return@repeat }` 로 구현하면
     * return@repeat 이 break 가 아니라 continue 라 20회(MAX_BATCHES)가 되어 여기서 죽는다(F-신규-4).
     * 또한 cutoff 를 루프 안에서 매번 now() 로 다시 계산하면 회차마다 기준이 밀리므로,
     * 세 번의 호출이 모두 같은 cutoff 를 쓰는지도 함께 잠근다.
     */
    @Test
    fun `배치가 가득 차면 다음 배치를 이어서 돌린다`() {
        val batchLimit = NotificationRetentionScheduler.BATCH_LIMIT
        whenever(notificationRepository.deleteSentBefore(any(), any()))
            .thenReturn(batchLimit, batchLimit, LAST_PARTIAL_BATCH)

        scheduler.purgeExpiredNotifications()

        val cutoffCaptor = argumentCaptor<LocalDateTime>()
        verify(notificationRepository, times(EXPECTED_ROUNDS)).deleteSentBefore(cutoffCaptor.capture(), any())
        assertEquals(
            listOf(cutoffCaptor.firstValue, cutoffCaptor.firstValue, cutoffCaptor.firstValue),
            cutoffCaptor.allValues,
            "cutoff 는 실행당 한 번만 계산해 모든 배치가 같은 기준을 써야 한다",
        )
    }

    /** R36 — 대상이 계속 가득 차더라도 1회 실행은 MAX_BATCHES 회에서 멈춘다(무한 루프 방지). */
    @Test
    fun `배치 상한을 넘겨 무한히 돌지 않는다`() {
        whenever(notificationRepository.deleteSentBefore(any(), any()))
            .thenReturn(NotificationRetentionScheduler.BATCH_LIMIT)

        scheduler.purgeExpiredNotifications()

        verify(notificationRepository, times(NotificationRetentionScheduler.MAX_BATCHES))
            .deleteSentBefore(any(), any())
        assertEquals(MAX_BATCHES_EXPECTED, NotificationRetentionScheduler.MAX_BATCHES)
    }

    /**
     * R37 — 삭제가 터져도 스케줄러 스레드는 살아야 한다.
     * 같은 이유로 실패한 배치를 그 자리에서 재시도하지 않고 중단한다(다음 주기에 재시도).
     */
    @Test
    fun `삭제가 실패해도 예외를 밖으로 던지지 않는다`() {
        whenever(notificationRepository.deleteSentBefore(any(), any()))
            .thenThrow(RuntimeException("정리 실패"))

        assertDoesNotThrow<Unit> { scheduler.purgeExpiredNotifications() }

        verify(notificationRepository, times(1)).deleteSentBefore(any(), any())
    }

    companion object {
        private const val BATCH_LIMIT_EXPECTED = 500
        private const val MAX_BATCHES_EXPECTED = 20
        private const val LAST_PARTIAL_BATCH = 200
        private const val EXPECTED_ROUNDS = 3
    }
}
