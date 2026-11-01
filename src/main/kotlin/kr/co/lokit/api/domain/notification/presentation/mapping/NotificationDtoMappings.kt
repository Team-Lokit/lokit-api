package kr.co.lokit.api.domain.notification.presentation.mapping

import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.dto.NotificationResponse

/**
 * application 계층이 dto 를 참조하면 ArchUnit 규칙 3 위반이다(경계면 #8).
 * 선례(NotificationSettingsDtoMappings.kt)대로 presentation 에 확장 함수로 둔다.
 * 숫자 PK(id)·recipientUserId·actorUserId 는 응답에 싣지 않는다.
 */
fun Notification.toResponse(): NotificationResponse =
    NotificationResponse(
        notifId = notifId,
        type = notificationType,
        title = title,
        body = body,
        groupCount = groupCount,
        isRead = isRead,
        sentAt = sentAt,
        targetPhotoId = targetPhotoId,
        targetAddress = targetAddress,
    )
