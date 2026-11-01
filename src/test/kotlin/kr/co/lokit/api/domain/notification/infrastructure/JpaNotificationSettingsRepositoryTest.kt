package kr.co.lokit.api.domain.notification.infrastructure

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
import kotlin.test.assertNull

/**
 * 어댑터 단위 테스트(계약 §4-D). 목킹 대상은 JpaRepository 하나뿐이다
 * (선례: JpaDeviceTokenRepositoryTest).
 * 유니크 제약·소프트삭제·컬럼 왕복 같은 DB 실측은 NotificationSettingsRepositoryTest(§4-E)가 맡는다.
 */
@ExtendWith(MockitoExtension::class)
class JpaNotificationSettingsRepositoryTest {
    @Mock
    lateinit var notificationSettingJpaRepository: NotificationSettingJpaRepository

    lateinit var repository: JpaNotificationSettingsRepository

    @BeforeEach
    fun setUp() {
        repository = JpaNotificationSettingsRepository(notificationSettingJpaRepository)
    }

    @Test
    fun `저장된 행이 없으면 널을 돌려준다`() {
        whenever(notificationSettingJpaRepository.findByUserId(1L)).thenReturn(null)

        assertNull(repository.findByUserId(1L))
    }

    @Test
    fun `설정이 없던 사용자는 새 행으로 저장한다`() {
        whenever(notificationSettingJpaRepository.findByUserId(1L)).thenReturn(null)
        whenever(notificationSettingJpaRepository.save(any<NotificationSettingEntity>())).thenAnswer { it.arguments[0] }

        repository.save(
            NotificationSettings(
                userId = 1L,
                masterEnabled = false,
                disabledTypes = setOf(NotificationType.UPLOAD, NotificationType.COMMENT),
            ),
        )

        // BaseEntity.equals 는 id 가 null 이면 항상 false 라 verify(repo).save(expected) 를 쓸 수 없다(계약 §6-3).
        val captor = argumentCaptor<NotificationSettingEntity>()
        verify(notificationSettingJpaRepository).save(captor.capture())
        assertEquals(1L, captor.firstValue.userId)
        assertEquals(false, captor.firstValue.masterEnabled)
        assertEquals("COMMENT,UPLOAD", captor.firstValue.disabledTypes)
    }

    @Test
    fun `설정이 있던 사용자는 더티체킹으로 갱신하고 save를 부르지 않는다`() {
        val existing = NotificationSettingEntity(userId = 1L, masterEnabled = true, disabledTypes = "")
        whenever(notificationSettingJpaRepository.findByUserId(1L)).thenReturn(existing)

        val saved =
            repository.save(
                NotificationSettings(
                    userId = 1L,
                    masterEnabled = false,
                    disabledTypes = setOf(NotificationType.REACTION),
                ),
            )

        assertEquals(false, existing.masterEnabled)
        assertEquals("REACTION", existing.disabledTypes)
        assertEquals(NotificationSettings(1L, false, setOf(NotificationType.REACTION)), saved)
        verify(notificationSettingJpaRepository, never()).save(any<NotificationSettingEntity>())
    }
}
