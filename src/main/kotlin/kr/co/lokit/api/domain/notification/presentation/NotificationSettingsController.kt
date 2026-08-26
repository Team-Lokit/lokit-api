package kr.co.lokit.api.domain.notification.presentation

import jakarta.validation.Valid
import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationSettingsUseCase
import kr.co.lokit.api.domain.notification.dto.NotificationSettingsResponse
import kr.co.lokit.api.domain.notification.dto.UpdateNotificationSettingsRequest
import kr.co.lokit.api.domain.notification.presentation.mapping.toResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("notification-settings")
class NotificationSettingsController(
    private val notificationSettingsUseCase: NotificationSettingsUseCase,
) : NotificationSettingsApi {
    @GetMapping
    override fun getNotificationSettings(@CurrentUserId userId: Long): NotificationSettingsResponse =
        notificationSettingsUseCase.getSettings(userId).toResponse()

    @PatchMapping
    override fun updateNotificationSettings(
        @CurrentUserId userId: Long,
        @RequestBody @Valid request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse =
        notificationSettingsUseCase
            .updateSettings(
                userId = userId,
                masterEnabled = request.masterEnabled,
                typeToggles = request.types ?: emptyMap(),
            ).toResponse()
}
