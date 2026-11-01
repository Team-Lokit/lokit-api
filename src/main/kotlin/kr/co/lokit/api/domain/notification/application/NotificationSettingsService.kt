package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.notification.application.port.NotificationSettingsRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.findOrDefault
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationSettingsUseCase
import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import kr.co.lokit.api.domain.notification.domain.NotificationType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationSettingsService(
    private val settingsRepository: NotificationSettingsRepositoryPort,
) : NotificationSettingsUseCase {
    /** readOnly — 절대 save 를 부르지 않는다(관찰 가능한 동작 #13, D1/D2 실측이 못박는다). */
    @Transactional(readOnly = true)
    override fun getSettings(userId: Long): NotificationSettings = settingsRepository.findOrDefault(userId)

    /**
     * 델타가 비어 있어도 save 를 호출한다(계약 Q3=(a)). 반환값이 항상 '저장된 실체'와 일치해야
     * 컨트롤러 응답이 거짓말을 하지 않는다. 신규 유저의 첫 PATCH 로 행이 처음 생긴다 —
     * 회원가입 훅에 손대지 않는 것이 이 설계의 목적이다.
     */
    @Transactional
    override fun updateSettings(
        userId: Long,
        masterEnabled: Boolean?,
        typeToggles: Map<NotificationType, Boolean>,
    ): NotificationSettings {
        val current = settingsRepository.findOrDefault(userId)
        return settingsRepository.save(current.update(masterEnabled, typeToggles))
    }
}
