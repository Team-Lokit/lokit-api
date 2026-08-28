package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.fixture.createDeviceToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실 DB(H2 MODE=PostgreSQL, ddl-auto=create-drop) 실측 테스트.
 * BaseEntity 의 @SoftDelete 와 token 유니크 제약의 상호작용은 목으로 원리적으로 검증 불가하므로
 * 이 슬라이스에서 유일하게 @DataJpaTest 를 쓴다.
 */
@DataJpaTest
@Import(JpaDeviceTokenRepository::class)
class DeviceTokenRepositoryTest {
    @Autowired
    lateinit var repository: DeviceTokenRepositoryPort

    @Autowired
    lateinit var deviceTokenJpaRepository: DeviceTokenJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private fun flushAndClear() {
        deviceTokenJpaRepository.flush()
        entityManager.clear()
    }

    /** @SoftDelete 필터를 우회해 실제 물리 행 수를 센다. count() 는 소프트삭제된 행을 빼고 세기 때문. */
    private fun rawRowCount(): Long =
        (entityManager.createNativeQuery("select count(*) from device_token").singleResult as Number).toLong()

    @Test
    fun `같은 토큰을 다른 사용자가 등록하면 행은 하나이고 소유자만 바뀐다`() {
        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID))
        flushAndClear()

        repository.upsert(createDeviceToken(userId = 2L, token = "fcm-1", platform = DevicePlatform.IOS))
        flushAndClear()

        assertEquals(1L, deviceTokenJpaRepository.count())
        assertTrue(repository.findAllByUserId(1L).isEmpty())
        assertEquals(DevicePlatform.IOS, repository.findAllByUserId(2L).single().platform)
    }

    @Test
    fun `토큰을 전부 삭제한 뒤 같은 토큰을 다시 등록할 수 있다`() {
        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID))
        flushAndClear()

        val deleted = repository.deleteAllByUserId(1L)
        flushAndClear()

        assertEquals(1, deleted)
        assertEquals(0L, deviceTokenJpaRepository.count())
        // 소프트삭제로 회귀하면 행이 물리적으로 남아 token 유니크 제약을 계속 점유한다.
        // count() 는 소프트삭제 행을 걸러 0을 돌려주므로 여기서만 회귀를 잡아낼 수 있다.
        assertEquals(0L, rawRowCount())

        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID))
        flushAndClear()

        assertEquals(1L, deviceTokenJpaRepository.count())
    }

    @Test
    fun `한 사용자가 여러 기기를 등록하면 전부 조회된다`() {
        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-1", platform = DevicePlatform.ANDROID))
        repository.upsert(createDeviceToken(userId = 1L, token = "fcm-2", platform = DevicePlatform.IOS))
        flushAndClear()

        assertEquals(2, repository.findAllByUserId(1L).size)
    }
}
