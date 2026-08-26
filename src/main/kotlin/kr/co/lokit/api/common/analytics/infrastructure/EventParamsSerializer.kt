package kr.co.lokit.api.common.analytics.infrastructure

import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 이벤트 파라미터 맵을 JSON 문자열로 직렬화한다.
 *
 * 주입되는 자동구성 [ObjectMapper] 는 `spring.jackson.default-property-inclusion=non_null`
 * 이 적용돼 있어 Map 의 null 값 엔트리를 드롭한다. 따라서 params 에 null 값을 넣어도
 * 결과 JSON 에 남는 것을 보장하지 않는다.
 */
@Component
class EventParamsSerializer(
    private val objectMapper: ObjectMapper,
) {
    fun serialize(params: Map<String, Any?>): String =
        if (params.isEmpty()) EMPTY_PARAMS else objectMapper.writeValueAsString(params)

    companion object {
        const val EMPTY_PARAMS: String = "{}"
    }
}
