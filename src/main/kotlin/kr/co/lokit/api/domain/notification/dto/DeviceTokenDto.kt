package kr.co.lokit.api.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kr.co.lokit.api.domain.notification.domain.DevicePlatform

@Schema(description = "디바이스 토큰 등록 요청")
data class RegisterDeviceTokenRequest(
    @field:NotBlank(message = "디바이스 토큰은 필수입니다.")
    @field:Size(max = 512, message = "디바이스 토큰은 512자 이내여야 합니다.")
    @Schema(description = "FCM 등록 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
    val token: String,
    @Schema(
        description = "디바이스 플랫폼",
        allowableValues = ["ANDROID", "IOS", "WEB"],
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val platform: DevicePlatform,
)
