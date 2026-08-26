package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationTypeTest {
    @Test
    fun `알림 타입은 COMMENT REACTION UPLOAD 세 가지다`() {
        val names = NotificationType.entries.map { it.name }

        assertEquals(listOf("COMMENT", "REACTION", "UPLOAD"), names)
    }

    @Test
    fun `이름 문자열로 알림 타입을 복원할 수 있다`() {
        assertEquals(NotificationType.COMMENT, NotificationType.valueOf("COMMENT"))
    }
}
