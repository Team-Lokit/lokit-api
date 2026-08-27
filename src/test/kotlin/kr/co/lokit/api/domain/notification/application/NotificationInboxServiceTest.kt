package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationInboxUseCase
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.fixture.createNotification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계약 §4 계층 D (R17~R23). 협력자가 포트 하나뿐이라 스프링 슬라이스가 필요 없다(계약 §3 T4).
 * @InjectMocks 대신 수동 조립 — 이 저장소의 서비스 테스트 공통 패턴이다
 * (NotificationSettingsServiceTest, NotificationDispatchServiceTest 선례).
 *
 * 이 테스트가 고정하는 계약:
 * - 필드 타입을 인바운드 포트 `NotificationInboxUseCase` 로 선언한다(§2-3) — 구현 클래스가
 *   그 인터페이스를 실제로 만족해야만 컴파일된다.
 * - `getInbox` 는 page/size 를 **nullable** 로 받아 서비스 안에서 한 번만 정규화한다.
 *   컨트롤러에 기본값을 두지 않는다는 결정(§2-3)의 반대편 못이다.
 * - 권한 검증은 **서비스 안**에서 일어난다(0-R-1). `@PreAuthorize` 로 구현하면
 *   @WebMvcTest 에서 거짓 초록이 되어 검증 자체가 불가능하다는 실측 근거에 따른 결정이다.
 *   → R22 는 그 결정 덕분에 결정론적으로 빨개진다.
 */
@ExtendWith(MockitoExtension::class)
class NotificationInboxServiceTest {
    @Mock
    lateinit var notificationRepository: NotificationRepositoryPort

    lateinit var service: NotificationInboxUseCase

    private val userId = 1L

    @BeforeEach
    fun setUp() {
        service = NotificationInboxService(notificationRepository)
    }

    /**
     * 🔴 R17. 기본값 규칙이 서비스 한 곳에만 있다는 것을 포트 호출 인자로 못박는다.
     * 스텁을 두지 않는다 — Mockito 기본값(빈 리스트 / 0L)으로 충분하고,
     * 스텁을 두면 STRICT_STUBS 하에서 "쓰지 않는 스텁" 위험만 는다.
     */
    @Test
    fun `기본 페이지와 크기로 조회한다`() {
        val result = service.getInbox(userId, null, null)

        verify(notificationRepository).findInboxPage(userId, 0, 20)
        assertEquals(0, result.page)
        assertEquals(20, result.size)
    }

    /**
     * 🔴 R18. 예외를 던지지 않는다 — 정규화다(B6).
     * size 상한 50 은 F-신규-5 방어이기도 하다: size=0 이 흘러들면 PageResult.totalPages 가
     * 0 나눗셈으로 쓰레기값이 된다(E12).
     */
    @Test
    fun `음수 페이지와 과대 크기를 정규화한다`() {
        val result = service.getInbox(userId, -1, 9999)

        verify(notificationRepository).findInboxPage(userId, 0, 50)
        assertEquals(0, result.page)
        assertEquals(50, result.size)
    }

    /**
     * 🔴 R19. content 는 findInboxPage 가, totalElements 는 countInbox 가 채운다.
     * 두 포트 호출이 모두 일어나야 파생값(totalPages/isLast)이 성립한다.
     * 기대값 3/false 는 리터럴로 고정한다 — 구현식을 어서션에서 다시 계산하면 동어반복이다.
     */
    @Test
    fun `페이지 메타에 총 개수를 담아 돌려준다`() {
        val page = listOf(createNotification(id = 5L, notifId = "notif-5"))
        whenever(notificationRepository.findInboxPage(userId, 1, 2)).thenReturn(page)
        whenever(notificationRepository.countInbox(userId)).thenReturn(5L)

        val result: PageResult<Notification> = service.getInbox(userId, 1, 2)

        assertEquals(page, result.content)
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5L, result.totalElements)
        assertEquals(3, result.totalPages)
        assertFalse(result.isLast)
        verify(notificationRepository).countInbox(userId)
    }

    /**
     * 🔴 R20. 서비스는 도메인을 변형하지 않는다(E3) — 숫자 PK 를 포트에 넘겨
     * 엔티티 더티체킹으로 전이시킨다. copy(isRead=true) + save 는 새 행 INSERT 다(F-신규-1).
     */
    @Test
    fun `자기 알림을 읽음 처리하면 포트에 위임한다`() {
        val notification = createNotification(id = 7L, notifId = NOTIF_ID, recipientUserId = userId, isRead = false)
        whenever(notificationRepository.findByNotifId(NOTIF_ID)).thenReturn(notification)
        whenever(notificationRepository.markAsRead(7L)).thenReturn(notification)

        service.markAsRead(userId, NOTIF_ID)

        verify(notificationRepository).markAsRead(7L)
    }

    /**
     * 🔴 R21. 멱등의 논리적 증명(물리적 증명은 R13 의 @Version 미증가가 한다).
     * markAsRead 를 스텁하면 STRICT_STUBS 가 미사용 스텁으로 실패시킨다 — never() 검증만 한다(§6).
     */
    @Test
    fun `이미 읽은 알림은 쓰기 없이 끝난다`() {
        val alreadyRead = createNotification(id = 7L, notifId = NOTIF_ID, recipientUserId = userId, isRead = true)
        whenever(notificationRepository.findByNotifId(NOTIF_ID)).thenReturn(alreadyRead)

        service.markAsRead(userId, NOTIF_ID)

        verify(notificationRepository, never()).markAsRead(any())
    }

    /**
     * 🔴 R22 ★결정론적 RED (0-R-1). 이 슬라이스가 저장소 관례(@PreAuthorize)를 의도적으로
     * 벗어나는 이유가 이 테스트다 — 관례를 따르면 구현 유무와 무관하게 항상 초록이 된다.
     *
     * 두 어서션은 같은 한 동작의 두 측면이다: 403 을 던진다는 것과, **던지기 전에 쓰지 않는다**는 것.
     * 소유자 확인이 markAsRead 호출 뒤로 밀리면 예외는 나도 DB 는 이미 바뀐다(§7 되돌리기 신호).
     */
    @Test
    fun `다른 사용자의 알림은 ForbiddenException으로 막는다`() {
        val othersNotification = createNotification(id = 7L, notifId = NOTIF_ID, recipientUserId = 2L, isRead = false)
        whenever(notificationRepository.findByNotifId(NOTIF_ID)).thenReturn(othersNotification)

        assertThrows<BusinessException.ForbiddenException> {
            service.markAsRead(userId, NOTIF_ID)
        }

        verify(notificationRepository, never()).markAsRead(any())
    }

    /**
     * 🔴 R23. 포트가 nullable 이라 스텁 없이도 성립하지만 명시적으로 thenReturn(null) 을 둔다(§6).
     * 404 는 ResourceNotFoundException 이다 — 새 ErrorCode 를 만들지 않는다(E19).
     */
    @Test
    fun `없는 notifId는 ResourceNotFoundException이다`() {
        whenever(notificationRepository.findByNotifId(UNKNOWN_NOTIF_ID)).thenReturn(null)

        val exception =
            assertThrows<BusinessException.ResourceNotFoundException> {
                service.markAsRead(userId, UNKNOWN_NOTIF_ID)
            }

        assertTrue(exception.message.contains(UNKNOWN_NOTIF_ID))
    }

    companion object {
        private const val NOTIF_ID = "notif-1"
        private const val UNKNOWN_NOTIF_ID = "notif-does-not-exist"
    }
}
