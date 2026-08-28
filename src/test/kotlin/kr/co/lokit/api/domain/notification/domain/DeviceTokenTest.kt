package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DeviceTokenTest {

    @Test
    fun `디바이스 토큰은 사용자와 토큰과 플랫폼을 가진다`() {
        val deviceToken = DeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID)

        assertEquals(1L, deviceToken.userId)
        assertEquals("fcm-1", deviceToken.token)
        assertEquals(DevicePlatform.ANDROID, deviceToken.platform)
        assertEquals(0L, deviceToken.id)
    }

    @Test
    fun `토큰이 공백이면 생성할 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            DeviceToken(userId = 1L, token = "  ", platform = DevicePlatform.ANDROID)
        }

        assertEquals("디바이스 토큰은 필수입니다.", exception.message)
    }

    @Test
    fun `토큰이 512자를 넘으면 생성할 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            DeviceToken(userId = 1L, token = "a".repeat(513), platform = DevicePlatform.ANDROID)
        }

        assertEquals("디바이스 토큰은 512자 이내여야 합니다.", exception.message)
    }
}
