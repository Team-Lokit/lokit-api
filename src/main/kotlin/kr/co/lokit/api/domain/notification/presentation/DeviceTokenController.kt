package kr.co.lokit.api.domain.notification.presentation

import jakarta.validation.Valid
import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.domain.notification.application.port.`in`.RegisterDeviceTokenUseCase
import kr.co.lokit.api.domain.notification.dto.RegisterDeviceTokenRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("device-tokens")
class DeviceTokenController(
    private val registerDeviceTokenUseCase: RegisterDeviceTokenUseCase,
) : DeviceTokenApi {
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun registerDeviceToken(@CurrentUserId userId: Long, @RequestBody @Valid request: RegisterDeviceTokenRequest) {
        registerDeviceTokenUseCase.register(userId = userId, token = request.token, platform = request.platform)
    }
}
