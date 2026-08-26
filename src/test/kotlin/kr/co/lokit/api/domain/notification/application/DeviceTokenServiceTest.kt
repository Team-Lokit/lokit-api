package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.domain.DeviceToken
import kr.co.lokit.api.fixture.createDeviceToken
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class DeviceTokenServiceTest {
    @Mock
    lateinit var deviceTokenRepository: DeviceTokenRepositoryPort

    @InjectMocks
    lateinit var deviceTokenService: DeviceTokenService

    @Test
    fun `디바이스 토큰을 등록하면 사용자와 토큰과 플랫폼이 그대로 저장된다`() {
        whenever(deviceTokenRepository.upsert(any())).thenReturn(createDeviceToken(id = 1L))

        deviceTokenService.register(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID)

        val captor = argumentCaptor<DeviceToken>()
        verify(deviceTokenRepository).upsert(captor.capture())
        assertEquals(1L, captor.firstValue.userId)
        assertEquals("fcm-1", captor.firstValue.token)
        assertEquals(DevicePlatform.ANDROID, captor.firstValue.platform)
    }

    @Test
    fun `사용자의 디바이스 토큰을 전부 삭제한다`() {
        deviceTokenService.deleteAllByUserId(7L)

        verify(deviceTokenRepository).deleteAllByUserId(7L)
    }
}
