package kr.co.lokit.api.domain.notification.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.domain.notification.dto.NotificationResponse

@SecurityRequirement(name = "Authorization")
@Tag(name = "Notification", description = "알림함 API")
interface NotificationApi {
    @Operation(
        summary = "알림함 목록 조회",
        description = "본인에게 발송된 알림을 최신순으로 내려줍니다. page/size 를 생략하면 서버 기본값을 사용합니다. " +
            "그룹 윈도우가 아직 열려 있는 알림은 본문과 groupCount 가 어긋날 수 있습니다.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
    ])
    fun getNotifications(
        @Parameter(hidden = true) userId: Long,
        page: Int?,
        size: Int?,
    ): PageResult<NotificationResponse>

    @Operation(
        summary = "알림 읽음 처리",
        description = "멱등합니다. 이미 읽은 알림을 다시 호출해도 204 입니다. 본인의 알림만 읽음 처리할 수 있습니다.",
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "읽음 처리 성공"),
        ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ApiResponse(responseCode = "403", description = "본인의 알림이 아님", content = [Content()]),
        ApiResponse(responseCode = "404", description = "알림을 찾을 수 없음", content = [Content()]),
    ])
    fun markNotificationAsRead(
        @Parameter(hidden = true) userId: Long,
        notifId: String,
    )
}
