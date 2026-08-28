package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.UploadNotificationService
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import kr.co.lokit.api.fixture.createPendingUploadNotification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 계약 T13 / 계층(7). 🔴 D3 회귀 전담 방지 테스트.
 *
 * 슬라이스3의 NotificationGroupWindowScheduler 는 읽기측에서 `now - 5분`을 뺀다.
 * 그것을 복사해 `minusMinutes(10)`을 넣으면 N-2 는 실제 발송이 T+20분이 된다 —
 * 산술은 이미 쓰기측(scheduledAt = now + 10분)에서 끝났기 때문이다.
 * 그래서 여기서는 캡처값이 **빼기 없는** 구간 [beforeCall, afterCall] 안에 있는지로 못박는다.
 * minusMinutes(N) 이 들어가는 순간 캡처값이 beforeCall 보다 앞서서 실패한다.
 */
@ExtendWith(MockitoExtension::class)
class PendingUploadNotificationSchedulerTest {
    @Mock
    lateinit var pendingRepository: PendingUploadNotificationRepositoryPort

    @Mock
    lateinit var uploadNotificationService: UploadNotificationService

    @InjectMocks
    lateinit var scheduler: PendingUploadNotificationScheduler

    @Test
    fun `findDuePendings에 넘어가는 scheduledAtBefore가 now 그 자체다`() {
        val pending = createPendingUploadNotification(id = 1L)
        whenever(pendingRepository.findDuePendings(any(), any())).thenReturn(listOf(pending))

        val beforeCall = LocalDateTime.now()
        scheduler.firePendingUploadNotifications()
        val afterCall = LocalDateTime.now()

        val cutoffCaptor = argumentCaptor<LocalDateTime>()
        val limitCaptor = argumentCaptor<Int>()
        verify(pendingRepository).findDuePendings(cutoffCaptor.capture(), limitCaptor.capture())

        val cutoff = cutoffCaptor.firstValue
        assertTrue(
            !cutoff.isBefore(beforeCall) && !cutoff.isAfter(afterCall),
            "scheduledAtBefore 는 now 그 자체여야 한다(D3) — 빼기 금지: cutoff=$cutoff, 호출구간=$beforeCall~$afterCall",
        )
        assertEquals(PendingUploadNotificationScheduler.BATCH_LIMIT, limitCaptor.firstValue)
        assertEquals(BATCH_LIMIT_EXPECTED, PendingUploadNotificationScheduler.BATCH_LIMIT)
        verify(uploadNotificationService).fire(eq(pending), any())
    }

    @Test
    fun `대상이 없으면 아무 것도 하지 않는다`() {
        whenever(pendingRepository.findDuePendings(any(), any())).thenReturn(emptyList())

        scheduler.firePendingUploadNotifications()

        verify(uploadNotificationService, never()).fire(any(), any())
    }

    @Test
    fun `한 건이 실패해도 나머지 마감을 계속한다`() {
        val first = createPendingUploadNotification(id = 1L, photoIds = listOf(11L))
        val second = createPendingUploadNotification(id = 2L, photoIds = listOf(12L))
        val third = createPendingUploadNotification(id = 3L, photoIds = listOf(13L))
        whenever(pendingRepository.findDuePendings(any(), any())).thenReturn(listOf(first, second, third))
        whenever(uploadNotificationService.fire(eq(second), any()))
            .thenThrow(RuntimeException("발송 실패"))

        assertDoesNotThrow<Unit> { scheduler.firePendingUploadNotifications() }

        verify(uploadNotificationService).fire(eq(first), any())
        verify(uploadNotificationService).fire(eq(third), any())
    }

    companion object {
        private const val BATCH_LIMIT_EXPECTED = 200
    }
}
