package kr.co.lokit.api.domain.notification.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.co.lokit.api.domain.notification.dto.NotificationSettingsResponse
import kr.co.lokit.api.domain.notification.dto.UpdateNotificationSettingsRequest

@SecurityRequirement(name = "Authorization")
@Tag(name = "NotificationSettings", description = "알림 설정 API")
interface NotificationSettingsApi {
    @Operation(
        summary = "알림 설정 조회",
        description = "마스터 스위치와 서버가 아는 모든 알림 종류의 스위치를 내려줍니다. " +
            "설정을 저장한 적 없는 사용자는 전부 켜진 기본값을 받습니다(행을 새로 만들지 않습니다).",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
    ])
    fun getNotificationSettings(@Parameter(hidden = true) userId: Long): NotificationSettingsResponse

    @Operation(
        summary = "알림 설정 부분 변경",
        description = "생략한 필드는 변경하지 않습니다. 빈 바디({})는 유효한 no-op 요청입니다. " +
            "마스터를 껐다 켜도 종류별 설정은 그대로 복원됩니다. 응답은 변경 후 전체 상태입니다.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "변경 성공 (변경 후 전체 상태)"),
        ApiResponse(responseCode = "400", description = "알 수 없는 알림 종류 키", content = [Content()]),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ApiResponse(responseCode = "409", description = "동시 변경 경합 (재시도하면 해소됨)", content = [Content()]),
    ])
    fun updateNotificationSettings(
        @Parameter(hidden = true) userId: Long,
        request: UpdateNotificationSettingsRequest,
    ): NotificationSettingsResponse
}
