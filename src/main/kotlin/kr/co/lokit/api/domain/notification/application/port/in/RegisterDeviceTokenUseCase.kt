package kr.co.lokit.api.domain.notification.application.port.`in`

import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.domain.DeviceToken

interface RegisterDeviceTokenUseCase {
    fun register(userId: Long, token: String, platform: DevicePlatform): DeviceToken
}
