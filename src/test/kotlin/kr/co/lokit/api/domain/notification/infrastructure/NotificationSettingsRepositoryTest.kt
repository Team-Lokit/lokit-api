package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.notification.application.port.NotificationSettingsRepositoryPort
import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.domain.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 실 DB(H2 MODE=PostgreSQL, ddl-auto=create-drop) 실측 테스트(계약 §4-E).
 * UNIQUE(user_id) 제약, disabled_types 문자열 컬럼 왕복, 마스터 토글 후 종류별 값 보존은
 * 목으로 원리적으로 검증 불가하다 — 선례 DeviceTokenRepositoryTest 와 같은 이유로 @DataJpaTest 를 쓴다.
 */
@DataJpaTest
@Import(JpaNotificationSettingsRepository::class)
class NotificationSettingsRepositoryTest {
    @Autowired
    lateinit var repository: NotificationSettingsRepositoryPort

    @Autowired
    lateinit var notificationSettingJpaRepository: NotificationSettingJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private fun flushAndClear() {
        notificationSettingJpaRepository.flush()
        entityManager.clear()
    }

    /** @SoftDelete 필터를 우회해 실제 물리 행 수를 센다. count() 는 소프트삭제된 행을 빼고 세기 때문. */
    private fun rawRowCount(): Long =
        (entityManager.createNativeQuery("select count(*) from notification_setting").singleResult as Number).toLong()

    @Test
    fun `저장한 적 없는 사용자를 조회해도 행이 생기지 않는다`() {
        val found = repository.findByUserId(1L)
        flushAndClear()

        assertNull(found)
        assertEquals(0L, rawRowCount())
    }

    @Test
    fun `같은 사용자를 두 번 저장해도 행은 하나다`() {
        repository.save(NotificationSettings(userId = 1L, masterEnabled = false))
        flushAndClear()

        repository.save(NotificationSettings(userId = 1L, masterEnabled = true, disabledTypes = setOf(NotificationType.COMMENT)))
        flushAndClear()

        // count() 는 @SoftDelete 행을 걸러 세므로 유니크 제약 회귀를 못 잡는다. 네이티브 카운트여야 한다.
        assertEquals(1L, rawRowCount())
        val found = repository.findByUserId(1L)!!
        assertEquals(true, found.masterEnabled)
        assertEquals(setOf(NotificationType.COMMENT), found.disabledTypes)
    }

    @Test
    fun `비활성 종류를 저장하고 다시 조회하면 그대로 복원된다`() {
        repository.save(
            NotificationSettings(
                userId = 1L,
                masterEnabled = true,
                disabledTypes = setOf(NotificationType.COMMENT, NotificationType.UPLOAD),
            ),
        )
        flushAndClear()

        val found = repository.findByUserId(1L)!!

        assertEquals(setOf(NotificationType.COMMENT, NotificationType.UPLOAD), found.disabledTypes)
        assertEquals(true, found.masterEnabled)
    }

    @Test
    fun `마스터를 껐다 켜도 비활성 종류 컬럼이 그대로 남는다`() {
        repository.save(
            NotificationSettings(userId = 1L, masterEnabled = true, disabledTypes = setOf(NotificationType.REACTION)),
        )
        flushAndClear()

        val turnedOff = repository.findByUserId(1L)!!.update(masterEnabled = false, typeToggles = emptyMap())
        repository.save(turnedOff)
        flushAndClear()

        val turnedOn = repository.findByUserId(1L)!!.update(masterEnabled = true, typeToggles = emptyMap())
        repository.save(turnedOn)
        flushAndClear()

        val restored = repository.findByUserId(1L)!!
        assertEquals(true, restored.masterEnabled)
        assertEquals(setOf(NotificationType.REACTION), restored.disabledTypes)
        assertEquals(1L, rawRowCount())
    }
}
