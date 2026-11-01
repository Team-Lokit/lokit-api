package kr.co.lokit.api.common.analytics.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import kr.co.lokit.api.common.entity.BaseEntity

@Entity(name = "AppEventLog")
@Table(
    indexes = [
        Index(columnList = "event_name"),
        Index(columnList = "user_id"),
        Index(columnList = "created_at"),
    ],
)
class AppEventLogEntity(
    @Column(name = "event_name", nullable = false, length = 100)
    val eventName: String,
    @Column(name = "user_id")
    val userId: Long? = null,
    @Column(name = "notif_id", length = 255)
    val notifId: String? = null,
    @Column(name = "notif_type", length = 32)
    val notifType: String? = null,
    @Column(name = "params", nullable = false, columnDefinition = "text")
    val params: String = EventParamsSerializer.EMPTY_PARAMS,
) : BaseEntity()
