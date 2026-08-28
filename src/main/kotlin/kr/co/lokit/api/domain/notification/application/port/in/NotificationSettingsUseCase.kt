package kr.co.lokit.api.domain.notification.application.port.`in`

import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.domain.NotificationType

/**
 * 컨트롤러 전용 인바운드 포트(선례: RegisterDeviceTokenUseCase ← DeviceTokenController).
 * NotificationDispatchService 게이트는 이 인터페이스를 쓰지 않는다 — 계약 0-3절 참고.
 */
interface NotificationSettingsUseCase {
    /** 저장된 행이 없어도 절대 null/예외가 아니다 — 기본값(전부 ON)을 합성해 돌려준다. */
    fun getSettings(userId: Long): NotificationSettings

    /**
     * 부분 업데이트. masterEnabled=null 이면 마스터 불변, typeToggles 가 비면 종류별 불변.
     * 반환값은 저장된 최종 상태다(컨트롤러가 그대로 응답으로 내린다).
     */
    fun updateSettings(
        userId: Long,
        masterEnabled: Boolean?,
        typeToggles: Map<NotificationType, Boolean>,
    ): NotificationSettings
}
