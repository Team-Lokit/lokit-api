package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.fixture.createDeviceToken
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

@ExtendWith(MockitoExtension::class)
class JpaDeviceTokenRepositoryTest {
    @Mock
    lateinit var deviceTokenJpaRepository: DeviceTokenJpaRepository

    lateinit var repository: JpaDeviceTokenRepository

    @BeforeEach
    fun setUp() {
        repository = JpaDeviceTokenRepository(deviceTokenJpaRepository)
    }

    @Test
    fun `등록된 적 없는 토큰이면 새 행으로 저장한다`() {
        whenever(deviceTokenJpaRepository.findByToken("fcm-1")).thenReturn(null)
        whenever(deviceTokenJpaRepository.save(any<DeviceTokenEntity>())).thenAnswer { it.arguments[0] }

        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID))

        val captor = argumentCaptor<DeviceTokenEntity>()
        verify(deviceTokenJpaRepository).save(captor.capture())
        assertEquals("fcm-1", captor.firstValue.token)
        assertEquals(1L, captor.firstValue.userId)
        assertEquals(DevicePlatform.ANDROID, captor.firstValue.platform)
    }

    @Test
    fun `이미 등록된 토큰이면 소유자를 바꾸고 새로 저장하지 않는다`() {
        val existing = DeviceTokenEntity(token = "fcm-1", userId = 1L, platform = DevicePlatform.ANDROID)
        whenever(deviceTokenJpaRepository.findByToken("fcm-1")).thenReturn(existing)

        repository.upsert(createDeviceToken(userId = 2L, token = "fcm-1", platform = DevicePlatform.IOS))

        assertEquals(2L, existing.userId)
        assertEquals(DevicePlatform.IOS, existing.platform)
        verify(deviceTokenJpaRepository, never()).save(any<DeviceTokenEntity>())
    }

    @Test
    fun `사용자의 토큰 목록을 도메인 모델로 돌려준다`() {
        whenever(deviceTokenJpaRepository.findAllByUserId(1L)).thenReturn(
            listOf(
                DeviceTokenEntity(token = "fcm-1", userId = 1L, platform = DevicePlatform.ANDROID),
                DeviceTokenEntity(token = "fcm-2", userId = 1L, platform = DevicePlatform.IOS),
            ),
        )

        val found = repository.findAllByUserId(1L)

        assertEquals(listOf("fcm-1", "fcm-2"), found.map { it.token })
        assertEquals(listOf(1L, 1L), found.map { it.userId })
        assertEquals(listOf(DevicePlatform.ANDROID, DevicePlatform.IOS), found.map { it.platform })
    }

    @Test
    fun `사용자 토큰 전체 삭제는 하드 삭제 쿼리로 위임된다`() {
        whenever(deviceTokenJpaRepository.hardDeleteAllByUserId(7L)).thenReturn(3)

        val deleted = repository.deleteAllByUserId(7L)

        assertEquals(3, deleted)
    }
}
