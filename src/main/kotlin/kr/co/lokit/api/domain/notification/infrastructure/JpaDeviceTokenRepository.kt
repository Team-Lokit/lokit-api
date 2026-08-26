package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.domain.DeviceToken
import org.springframework.stereotype.Repository

@Repository
class JpaDeviceTokenRepository(
    private val deviceTokenJpaRepository: DeviceTokenJpaRepository,
) : DeviceTokenRepositoryPort {
    /**
     * 동시성: 같은 token 동시 신규등록 시 유니크 제약 위반 가능. 잡지 않고 전파한다 —
     * ErrorControllerAdvice가 409로 매핑, 클라이언트는 다음 앱 실행 시 재등록해 자가치유 (OQ-1).
     */
    override fun upsert(deviceToken: DeviceToken): DeviceToken {
        val existing = deviceTokenJpaRepository.findByToken(deviceToken.token)
        if (existing != null) {
            existing.userId = deviceToken.userId
            existing.platform = deviceToken.platform
            return existing.toDomain()
        }
        return deviceTokenJpaRepository.save(
            DeviceTokenEntity(token = deviceToken.token, userId = deviceToken.userId, platform = deviceToken.platform),
        ).toDomain()
    }

    override fun deleteAllByUserId(userId: Long): Int = deviceTokenJpaRepository.hardDeleteAllByUserId(userId)

    override fun findAllByUserId(userId: Long): List<DeviceToken> =
        deviceTokenJpaRepository.findAllByUserId(userId).map { it.toDomain() }

    private fun DeviceTokenEntity.toDomain(): DeviceToken =
        DeviceToken(id = id ?: 0L, userId = userId, token = token, platform = platform)
}
