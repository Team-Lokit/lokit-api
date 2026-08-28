package kr.co.lokit.api.domain.notification.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.co.lokit.api.domain.notification.dto.RegisterDeviceTokenRequest

@SecurityRequirement(name = "Authorization")
@Tag(name = "DeviceToken", description = "푸시 알림 디바이스 토큰 API")
interface DeviceTokenApi {
    @Operation(
        summary = "디바이스 토큰 등록",
        description = "FCM 토큰을 등록합니다. 로그인 직후와 앱 실행 시마다 호출하면 되며 멱등합니다. " +
            "삭제 API는 없으며 로그아웃 시 자동으로 전부 삭제됩니다.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "등록 성공"),
        ApiResponse(responseCode = "400", description = "토큰 누락/길이초과, 잘못된 platform 값", content = [Content()]),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ApiResponse(responseCode = "409", description = "동시 등록 경합 (재시도하면 해소됨)", content = [Content()]),
    ])
    fun registerDeviceToken(@Parameter(hidden = true) userId: Long, request: RegisterDeviceTokenRequest)
}
