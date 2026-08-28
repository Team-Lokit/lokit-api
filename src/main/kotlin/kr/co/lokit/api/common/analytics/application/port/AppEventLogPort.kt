package kr.co.lokit.api.common.analytics.application.port

/**
 * params 에 null 값을 가진 엔트리를 넣어도 저장 시 보존을 보장하지 않는다
 * (spring.jackson.default-property-inclusion=non_null 이 Map의 null 값도 억제한다).
 */
interface AppEventLogPort {
    fun record(
        eventName: String,
        userId: Long? = null,
        notifId: String? = null,
        notifType: String? = null,
        params: Map<String, Any?> = emptyMap(),
    )
}
