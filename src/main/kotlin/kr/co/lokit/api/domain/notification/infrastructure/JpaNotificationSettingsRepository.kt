package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.domain.notification.application.port.NotificationSettingsRepositoryPort
import kr.co.lokit.api.domain.notification.domain.NotificationSettings
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaNotificationSettingsRepository(
    private val notificationSettingJpaRepository: NotificationSettingJpaRepository,
) : NotificationSettingsRepositoryPort {
    @Transactional(readOnly = true)
    override fun findByUserId(userId: Long): NotificationSettings? =
        notificationSettingJpaRepository.findByUserId(userId)?.toDomain()

    /** 기존 행은 더티체킹으로 갱신한다 — save를 부르지 않는다(선례 계약 2-11). */
    @Transactional
    override fun save(settings: NotificationSettings): NotificationSettings {
        val encoded = NotificationSettings.encodeDisabledTypes(settings.disabledTypes)
        val existing = notificationSettingJpaRepository.findByUserId(settings.userId)
        if (existing != null) {
            existing.masterEnabled = settings.masterEnabled
            existing.disabledTypes = encoded
            return existing.toDomain()
        }
        return notificationSettingJpaRepository
            .save(
                NotificationSettingEntity(
                    userId = settings.userId,
                    masterEnabled = settings.masterEnabled,
                    disabledTypes = encoded,
                ),
            ).toDomain()
    }

    private fun NotificationSettingEntity.toDomain(): NotificationSettings =
        NotificationSettings(
            userId = userId,
            masterEnabled = masterEnabled,
            disabledTypes = NotificationSettings.decodeDisabledTypes(disabledTypes),
        )
}
