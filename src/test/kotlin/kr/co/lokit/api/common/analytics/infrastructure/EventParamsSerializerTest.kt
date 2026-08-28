package kr.co.lokit.api.common.analytics.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

class EventParamsSerializerTest {
    private val objectMapper = JsonMapper.builder().build()
    private val eventParamsSerializer = EventParamsSerializer(objectMapper)

    @Test
    fun `파라미터 맵을 JSON 으로 직렬화하면 값이 그대로 복원된다`() {
        val params =
            mapOf(
                "photoId" to 42,
                "albumName" to "제주도",
                "isOwner" to true,
                "score" to 1.5,
            )

        val json = eventParamsSerializer.serialize(params)

        val restored = objectMapper.readValue<Map<String, Any?>>(json)
        assertEquals(42, restored["photoId"])
        assertEquals("제주도", restored["albumName"])
        assertEquals(true, restored["isOwner"])
        assertEquals(1.5, restored["score"])
    }

    @Test
    fun `빈 파라미터 맵은 빈 JSON 객체 문자열이 된다`() {
        assertEquals("{}", eventParamsSerializer.serialize(emptyMap<String, Any?>()))
    }
}
