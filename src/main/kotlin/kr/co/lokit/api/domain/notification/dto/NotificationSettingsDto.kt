package kr.co.lokit.api.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import kr.co.lokit.api.domain.notification.domain.NotificationType

@Schema(description = "알림 설정 응답")
data class NotificationSettingsResponse(
    @Schema(description = "전체 알림 마스터 스위치", requiredMode = Schema.RequiredMode.REQUIRED)
    val masterEnabled: Boolean,
    @Schema(
        description = "알림 종류별 스위치. 서버가 아는 모든 종류의 값을 항상 명시적으로 내려준다. " +
            "저장된 적 없는 종류는 true 다. null 을 절대 포함하지 않는다(prod non_null 직렬화와 무관하게).",
        example = """{"COMMENT": true, "REACTION": true, "UPLOAD": false}""",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val types: Map<NotificationType, Boolean>,
)

@Schema(description = "알림 설정 부분 변경 요청. 생략한 필드는 변경하지 않는다.")
data class UpdateNotificationSettingsRequest(
    @Schema(description = "전체 알림 마스터 스위치. 생략 시 변경 없음.", nullable = true)
    val masterEnabled: Boolean? = null,
    @Schema(
        description = "변경할 종류만 담는다. 여기 없는 종류는 변경되지 않는다. 생략 시 변경 없음.",
        example = """{"COMMENT": false}""",
        nullable = true,
    )
    val types: Map<NotificationType, Boolean>? = null,
)
