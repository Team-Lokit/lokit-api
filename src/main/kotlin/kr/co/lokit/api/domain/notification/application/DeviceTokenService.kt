package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.`in`.DeleteDeviceTokensUseCase
import kr.co.lokit.api.domain.notification.application.port.`in`.RegisterDeviceTokenUseCase
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.domain.DeviceToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepositoryPort,
) : RegisterDeviceTokenUseCase, DeleteDeviceTokensUseCase {
    @Transactional
    override fun register(userId: Long, token: String, platform: DevicePlatform): DeviceToken =
        deviceTokenRepository.upsert(DeviceToken(userId = userId, token = token, platform = platform))

    @Transactional
    override fun deleteAllByUserId(userId: Long) {
        deviceTokenRepository.deleteAllByUserId(userId)
    }
}
