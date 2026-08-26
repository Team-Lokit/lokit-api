package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevicePlatformTest {
    @Test
    fun `디바이스 플랫폼은 ANDROID 와 IOS 두 가지다`() {
        val names = DevicePlatform.entries.map { it.name }

        assertEquals(listOf("ANDROID", "IOS"), names)
    }

    @Test
    fun `플랫폼 이름 문자열로 값을 복원할 수 있다`() {
        assertEquals(DevicePlatform.IOS, DevicePlatform.valueOf("IOS"))
    }
}
