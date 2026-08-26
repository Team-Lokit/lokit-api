package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.application.port.NotificationSettingsRepositoryPort
import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.domain.NotificationType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계약 §4-B (B1~B5). 협력자는 포트 하나뿐이라 스프링 슬라이스가 필요 없다(계약 §3).
 * @InjectMocks 대신 수동 조립 — 이 저장소의 서비스 테스트 공통 패턴이다
 * (UploadNotificationServiceTest, NotificationDispatchServiceTest 선례).
 *
 * 이 테스트가 고정하는 계약:
 * - 포트 `findByUserId` 는 **nullable** 을 돌려준다(§2-2, ★0-3). 기본값 합성은 서비스가 한다.
 * - GET 경로(getSettings)는 절대 `save` 를 부르지 않는다(관찰 가능한 동작 #13).
 */
@ExtendWith(MockitoExtension::class)
class NotificationSettingsServiceTest {
    @Mock
    lateinit var settingsRepository: NotificationSettingsRepositoryPort

    lateinit var service: NotificationSettingsService

    private val userId = 1L

    @BeforeEach
    fun setUp() {
        service = NotificationSettingsService(settingsRepository)
    }

    /**
     * 🔴 B1. 두 가지를 한 동작으로 못박는다 — "행이 없어도 기본값을 돌려준다"와
     * "그 기본값을 저장하지 않는다". lazy insert 를 하면 GET 이 쓰기 트랜잭션이 되고
     * 실측 E0(`rawRowCount() == 0`)이 무너진다.
     */
    @Test
    fun `저장된 설정이 없으면 기본값 설정을 돌려주고 저장하지 않는다`() {
        whenever(settingsRepository.findByUserId(userId)).thenReturn(null)

        val result = service.getSettings(userId)

        assertEquals(userId, result.userId)
        assertEquals(NotificationSettings.defaultsFor(userId), result)
        assertTrue(NotificationType.entries.all { result.isPushEnabledFor(it) })
        verify(settingsRepository, never()).save(any())
    }

    /** 🔴 B2. 기본값 합성이 저장된 값을 덮어쓰지 않는다 — `?:` 가 `?:` 로만 동작해야 한다. */
    @Test
    fun `저장된 설정이 있으면 그대로 돌려준다`() {
        val stored = settings(masterEnabled = false, disabledTypes = setOf(NotificationType.COMMENT))
        whenever(settingsRepository.findByUserId(userId)).thenReturn(stored)

        val result = service.getSettings(userId)

        assertEquals(stored, result)
        assertFalse(result.masterEnabled)
        assertFalse(result.isTypeEnabled(NotificationType.COMMENT))
        verify(settingsRepository, never()).save(any())
    }

    /**
     * 🔴 B3. 마스터만 담긴 sparse PATCH. 저장 인자를 captor 로 열어 disabledTypes 가
     * 그대로인지 본다 — 반환값만 보면 서비스가 저장은 엉뚱하게 하고 응답만 맞출 수 있다.
     */
    @Test
    fun `마스터만 변경하면 종류별 설정을 유지한 채 저장한다`() {
        whenever(settingsRepository.findByUserId(userId))
            .thenReturn(settings(masterEnabled = true, disabledTypes = setOf(NotificationType.COMMENT)))
        stubSaveEcho()

        val result = service.updateSettings(userId, masterEnabled = false, typeToggles = emptyMap())

        val saved = captureSaved()
        assertEquals(userId, saved.userId)
        assertFalse(saved.masterEnabled)
        assertEquals(setOf(NotificationType.COMMENT), saved.disabledTypes)
        assertEquals(saved, result)
    }

    /** 🔴 B4. B3 의 반대 방향. 종류만 담긴 PATCH 가 마스터를 건드리지 않는다. */
    @Test
    fun `종류만 변경하면 마스터를 유지한 채 저장한다`() {
        whenever(settingsRepository.findByUserId(userId))
            .thenReturn(settings(masterEnabled = false, disabledTypes = emptySet()))
        stubSaveEcho()

        val result =
            service.updateSettings(
                userId,
                masterEnabled = null,
                typeToggles = mapOf(NotificationType.REACTION to false),
            )

        val saved = captureSaved()
        assertFalse(saved.masterEnabled)
        assertEquals(setOf(NotificationType.REACTION), saved.disabledTypes)
        assertTrue(saved.isTypeEnabled(NotificationType.COMMENT))
        assertEquals(saved, result)
    }

    /**
     * 🔴 B5. 행이 한 번도 없던 유저의 첫 PATCH 로 행이 처음 생긴다 —
     * 회원가입 훅에 기본행 사전 생성을 넣지 않는 것이 이 설계의 목적이다(§2-4 KDoc).
     */
    @Test
    fun `저장된 적 없는 사용자가 변경하면 기본값 위에 변경분을 얹어 저장한다`() {
        whenever(settingsRepository.findByUserId(userId)).thenReturn(null)
        stubSaveEcho()

        val result =
            service.updateSettings(
                userId,
                masterEnabled = null,
                typeToggles = mapOf(NotificationType.UPLOAD to false),
            )

        val saved = captureSaved()
        assertEquals(userId, saved.userId)
        assertEquals(NotificationSettings.DEFAULT_MASTER_ENABLED, saved.masterEnabled)
        assertEquals(setOf(NotificationType.UPLOAD), saved.disabledTypes)
        assertTrue(saved.isTypeEnabled(NotificationType.COMMENT))
        assertEquals(saved, result)
    }

    private fun settings(
        masterEnabled: Boolean = true,
        disabledTypes: Set<NotificationType> = emptySet(),
    ) = NotificationSettings(
        userId = userId,
        masterEnabled = masterEnabled,
        disabledTypes = disabledTypes,
    )

    /** 어댑터는 저장된 실체를 돌려준다 — 목도 같은 계약을 흉내낸다. */
    private fun stubSaveEcho() {
        whenever(settingsRepository.save(any()))
            .thenAnswer { it.getArgument<NotificationSettings>(0) }
    }

    private fun captureSaved(): NotificationSettings {
        val captor = argumentCaptor<NotificationSettings>()
        verify(settingsRepository).save(captor.capture())
        return captor.firstValue
    }
}
