package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationSettingsTest {

    /**
     * 유저 id 는 이 도메인의 자연키일 뿐 판정에 관여하지 않는다.
     * 모든 케이스가 같은 상수를 쓰게 해서 "어느 유저냐"가 실패 원인이 될 여지를 없앤다.
     */
    private val userId = 1L

    private fun settings(
        masterEnabled: Boolean = true,
        disabledTypes: Set<NotificationType> = emptySet(),
    ) = NotificationSettings(
        userId = userId,
        masterEnabled = masterEnabled,
        disabledTypes = disabledTypes,
    )

    @Test
    fun `기본 설정은 마스터와 모든 종류가 켜져 있다`() {
        val defaults = NotificationSettings.defaultsFor(userId)

        assertEquals(userId, defaults.userId)
        assertEquals(NotificationSettings.DEFAULT_MASTER_ENABLED, defaults.masterEnabled)
        assertEquals(emptySet(), defaults.disabledTypes)
        assertTrue(NotificationType.entries.all { defaults.isPushEnabledFor(it) })
    }

    @Test
    fun `종류를 끄면 그 종류만 꺼지고 나머지는 켜진 채다`() {
        val updated =
            NotificationSettings
                .defaultsFor(userId)
                .update(masterEnabled = null, typeToggles = mapOf(NotificationType.COMMENT to false))

        assertFalse(updated.isTypeEnabled(NotificationType.COMMENT))
        assertTrue(updated.isTypeEnabled(NotificationType.REACTION))
        assertTrue(updated.isTypeEnabled(NotificationType.UPLOAD))
    }

    /** 커버리지 리뷰(슬라이스8)로 못박은 경로: 껐던 종류를 다시 켜면 disabledTypes에서 실제로 빠진다. */
    @Test
    fun `꺼져 있던 종류를 켜면 다시 활성화된다`() {
        val allDisabled = settings(disabledTypes = setOf(NotificationType.COMMENT, NotificationType.REACTION))

        val updated =
            allDisabled.update(masterEnabled = null, typeToggles = mapOf(NotificationType.COMMENT to true))

        assertTrue(updated.isTypeEnabled(NotificationType.COMMENT))
        assertFalse(updated.isTypeEnabled(NotificationType.REACTION))
    }

    @Test
    fun `마스터가 꺼지면 종류가 켜져 있어도 유효 발송은 거짓이다`() {
        val master = settings(masterEnabled = false)

        assertTrue(master.isTypeEnabled(NotificationType.COMMENT))
        assertFalse(master.isPushEnabledFor(NotificationType.COMMENT))
    }

    @Test
    fun `마스터를 껐다 켜도 종류별 설정이 그대로 복원된다`() {
        val customized =
            NotificationSettings
                .defaultsFor(userId)
                .update(masterEnabled = null, typeToggles = mapOf(NotificationType.COMMENT to false))

        val turnedOff = customized.update(masterEnabled = false, typeToggles = emptyMap())
        val turnedBackOn = turnedOff.update(masterEnabled = true, typeToggles = emptyMap())

        assertTrue(turnedBackOn.masterEnabled)
        assertFalse(turnedBackOn.isTypeEnabled(NotificationType.COMMENT))
        assertFalse(turnedBackOn.isPushEnabledFor(NotificationType.COMMENT))
        assertTrue(turnedBackOn.isPushEnabledFor(NotificationType.REACTION))
    }

    @Test
    fun `종류만 바꾸면 마스터는 변하지 않는다`() {
        val master = settings(masterEnabled = false)

        val updated =
            master.update(masterEnabled = null, typeToggles = mapOf(NotificationType.COMMENT to false))

        assertFalse(updated.masterEnabled)
        assertFalse(updated.isTypeEnabled(NotificationType.COMMENT))
    }

    @Test
    fun `저장된 값이 없는 종류는 켜진 것으로 읽는다`() {
        val onlyCommentDisabled = settings(disabledTypes = setOf(NotificationType.COMMENT))

        NotificationType.entries
            .filterNot { it == NotificationType.COMMENT }
            .forEach {
                assertEquals(NotificationSettings.DEFAULT_TYPE_ENABLED, onlyCommentDisabled.isTypeEnabled(it))
            }
    }

    @Test
    fun `알려진 모든 알림 종류가 스위치 목록에 빠짐없이 들어간다`() {
        val toggles = settings(disabledTypes = setOf(NotificationType.COMMENT)).typeToggles()

        assertEquals(NotificationType.entries.size, toggles.size)
        assertEquals(NotificationType.entries.toSet(), toggles.keys)
        assertEquals(false, toggles[NotificationType.COMMENT])
        assertEquals(true, toggles[NotificationType.REACTION])
    }

    @Test
    fun `비활성 종류 목록을 문자열로 왕복시켜도 동일하다`() {
        val disabled = setOf(NotificationType.UPLOAD, NotificationType.COMMENT)

        val encoded = NotificationSettings.encodeDisabledTypes(disabled)

        assertEquals("COMMENT,UPLOAD", encoded)
        assertEquals(disabled, NotificationSettings.decodeDisabledTypes(encoded))
    }

    @Test
    fun `알 수 없는 종류 이름은 무시하고 디코딩한다`() {
        assertEquals(
            setOf(NotificationType.COMMENT),
            NotificationSettings.decodeDisabledTypes("COMMENT,REMIND"),
        )
        assertEquals(emptySet(), NotificationSettings.decodeDisabledTypes(""))
    }
}
