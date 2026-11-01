package kr.co.lokit.api.domain.notification.scheduler

import kr.co.lokit.api.domain.notification.application.NotificationDispatchService
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.fixture.createNotification
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 계약 T9. 협력자가 2개(리포지토리 포트 + 디스패치 서비스)뿐이라 @InjectMocks 를 쓴다 —
 * 계층5 NotificationDispatchServiceTest 의 수동 조립은 LockManager 실 인스턴스가 필요했던
 * 그 서비스 내부 사정이고, 여기서는 서비스를 통째로 목으로 세우므로 해당되지 않는다.
 *
 * cutoff 는 스케줄러가 만드는 LocalDateTime.now() 파생값이라 고정할 수 없다.
 * 호출 전후 시각으로 구간을 만들어 캡처값이 그 안에 들어오는지로 검증한다.
 */
@ExtendWith(MockitoExtension::class)
class NotificationGroupWindowSchedulerTest {
    @Mock
    lateinit var notificationRepository: NotificationRepositoryPort

    @Mock
    lateinit var notificationDispatchService: NotificationDispatchService

    @InjectMocks
    lateinit var scheduler: NotificationGroupWindowScheduler

    @Test
    fun `5분이 지난 윈도우를 조회해 마감을 요청한다`() {
        val first = createNotification(id = 1L, notifId = "notif-1")
        val second = createNotification(id = 2L, notifId = "notif-2")
        whenever(notificationRepository.findClosableGroupWindows(any(), any()))
            .thenReturn(listOf(first, second))
        whenever(notificationDispatchService.closeGroupWindow(any(), any()))
            .thenReturn(first, second)

        val beforeCall = LocalDateTime.now()
        scheduler.closeExpiredGroupWindows()
        val afterCall = LocalDateTime.now()

        val cutoffCaptor = argumentCaptor<LocalDateTime>()
        val limitCaptor = argumentCaptor<Int>()
        verify(notificationRepository).findClosableGroupWindows(cutoffCaptor.capture(), limitCaptor.capture())

        val cutoff = cutoffCaptor.firstValue
        assertTrue(
            !cutoff.isBefore(beforeCall.minusMinutes(Notification.GROUP_WINDOW_MINUTES)) &&
                !cutoff.isAfter(afterCall.minusMinutes(Notification.GROUP_WINDOW_MINUTES)),
            "cutoff 는 호출 시점의 5분 전이어야 한다: cutoff=$cutoff, 호출구간=$beforeCall~$afterCall",
        )
        assertEquals(NotificationGroupWindowScheduler.BATCH_LIMIT, limitCaptor.firstValue)
        assertEquals(BATCH_LIMIT_EXPECTED, NotificationGroupWindowScheduler.BATCH_LIMIT)
        verify(notificationDispatchService, times(2)).closeGroupWindow(any(), any())
    }

    @Test
    fun `마감 대상이 없으면 아무 것도 하지 않는다`() {
        whenever(notificationRepository.findClosableGroupWindows(any(), any()))
            .thenReturn(emptyList())

        scheduler.closeExpiredGroupWindows()

        verify(notificationDispatchService, never()).closeGroupWindow(any(), any())
    }

    @Test
    fun `한 건이 실패해도 나머지 마감을 계속한다`() {
        val first = createNotification(id = 1L, notifId = "notif-1")
        val second = createNotification(id = 2L, notifId = "notif-2")
        val third = createNotification(id = 3L, notifId = "notif-3")
        whenever(notificationRepository.findClosableGroupWindows(any(), any()))
            .thenReturn(listOf(first, second, third))
        whenever(notificationDispatchService.closeGroupWindow(eq(first), any())).thenReturn(first)
        whenever(notificationDispatchService.closeGroupWindow(eq(second), any()))
            .thenThrow(RuntimeException("마감 실패"))
        whenever(notificationDispatchService.closeGroupWindow(eq(third), any())).thenReturn(third)

        assertDoesNotThrow<Unit> { scheduler.closeExpiredGroupWindows() }

        verify(notificationDispatchService).closeGroupWindow(eq(first), any())
        verify(notificationDispatchService).closeGroupWindow(eq(third), any())
    }

    companion object {
        private const val BATCH_LIMIT_EXPECTED = 200
    }
}
