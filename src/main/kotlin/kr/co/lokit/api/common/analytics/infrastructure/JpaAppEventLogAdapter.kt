package kr.co.lokit.api.common.analytics.infrastructure

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import org.springframework.stereotype.Repository

@Repository
class JpaAppEventLogAdapter(
    private val appEventLogJpaRepository: AppEventLogJpaRepository,
    private val eventParamsSerializer: EventParamsSerializer,
) : AppEventLogPort {
    override fun record(
        eventName: String,
        userId: Long?,
        notifId: String?,
        notifType: String?,
        params: Map<String, Any?>,
    ) {
        appEventLogJpaRepository.save(
            AppEventLogEntity(
                eventName = eventName,
                userId = userId,
                notifId = notifId,
                notifType = notifType,
                params = eventParamsSerializer.serialize(params),
            ),
        )
    }
}
