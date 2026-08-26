package kr.co.lokit.api.domain.notification.presentation.mapping

import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.dto.NotificationSettingsResponse

/**
 * application 계층이 dto 를 참조하면 ArchUnit 규칙 3 위반이다(경계면 #8).
 * 선례(CoupleDtoMappings.kt)대로 presentation 에 확장 함수로 둔다.
 */
fun NotificationSettings.toResponse(): NotificationSettingsResponse =
    NotificationSettingsResponse(
        masterEnabled = masterEnabled,
        types = typeToggles(),
    )
