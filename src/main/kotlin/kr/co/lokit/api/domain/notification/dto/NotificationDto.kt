package kr.co.lokit.api.domain.notification.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import kr.co.lokit.api.domain.notification.domain.NotificationType
import java.time.LocalDateTime

@Schema(description = "알림함 항목")
data class NotificationResponse(
    @Schema(
        description = "알림 식별자(UUID). 읽음 처리 API 의 경로 변수이자 FCM data payload 의 notifId 와 같은 값",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val notifId: String,
    @Schema(description = "알림 종류", requiredMode = Schema.RequiredMode.REQUIRED)
    val type: NotificationType,
    @Schema(description = "알림 제목", requiredMode = Schema.RequiredMode.REQUIRED)
    val title: String,
    @Schema(
        description = "알림 본문. 그룹 윈도우가 아직 열려 있으면 첫 이벤트 시점 문구다(최대 5분).",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val body: String,
    @Schema(
        description = "묶인 이벤트 개수. 마감 전에는 body 와 개수가 어긋날 수 있다.",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupCount: Int,
    // @get: 이어야 Kotlin data class 게터에 붙는다. 없으면 Jackson 이 "read" 로 내려보낸다(PageResult.kt:13 과 동일한 방어).
    @Schema(description = "읽음 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    @get:JsonProperty("isRead")
    val isRead: Boolean,
    @Schema(description = "발송 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    val sentAt: LocalDateTime,
    @Schema(description = "딥링크 대상 사진 id", requiredMode = Schema.RequiredMode.REQUIRED)
    val targetPhotoId: Long,
    @Schema(description = "딥링크 힌트 주소. null 이면 prod 응답에서 필드 자체가 생략된다(non_null).", nullable = true)
    val targetAddress: String?,
)
