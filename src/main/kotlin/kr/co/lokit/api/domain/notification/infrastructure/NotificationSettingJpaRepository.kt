package kr.co.lokit.api.domain.notification.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingJpaRepository : JpaRepository<NotificationSettingEntity, Long> {
    fun findByUserId(userId: Long): NotificationSettingEntity?
}
