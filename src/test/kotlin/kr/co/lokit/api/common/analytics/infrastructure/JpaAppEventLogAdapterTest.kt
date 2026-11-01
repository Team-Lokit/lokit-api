package kr.co.lokit.api.common.analytics.infrastructure

import kr.co.lokit.api.domain.notification.domain.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@ExtendWith(MockitoExtension::class)
class JpaAppEventLogAdapterTest {
    @Mock
    lateinit var appEventLogJpaRepository: AppEventLogJpaRepository

    private val objectMapper = JsonMapper.builder().build()

    lateinit var adapter: JpaAppEventLogAdapter

    @BeforeEach
    fun setUp() {
        adapter =
            JpaAppEventLogAdapter(
                appEventLogJpaRepository,
                EventParamsSerializer(objectMapper),
            )
    }

    @Test
    fun `이벤트를 기록하면 이벤트명을 담은 로그가 한 번 저장된다`() {
        adapter.record("push_sent")

        val captor = argumentCaptor<AppEventLogEntity>()
        verify(appEventLogJpaRepository).save(captor.capture())
        assertEquals("push_sent", captor.firstValue.eventName)
    }

    @Test
    fun `유저와 알림 식별 정보가 로그에 그대로 담긴다`() {
        adapter.record(
            "push_opened",
            userId = 7L,
            notifId = "n-123",
            notifType = NotificationType.COMMENT.name,
        )

        val captor = argumentCaptor<AppEventLogEntity>()
        verify(appEventLogJpaRepository).save(captor.capture())
        assertEquals(7L, captor.firstValue.userId)
        assertEquals("n-123", captor.firstValue.notifId)
        assertEquals("COMMENT", captor.firstValue.notifType)
    }

    @Test
    fun `파라미터 맵은 JSON 문자열로 직렬화되어 저장된다`() {
        adapter.record("push_sent", params = mapOf("photoId" to 42))

        val captor = argumentCaptor<AppEventLogEntity>()
        verify(appEventLogJpaRepository).save(captor.capture())
        val restored = objectMapper.readValue<Map<String, Any?>>(captor.firstValue.params)
        assertEquals(42, restored["photoId"])
    }

    @Test
    fun `선택 필드를 넘기지 않으면 널로 저장되고 파라미터는 빈 객체가 된다`() {
        adapter.record("app_opened")

        val captor = argumentCaptor<AppEventLogEntity>()
        verify(appEventLogJpaRepository).save(captor.capture())
        assertNull(captor.firstValue.userId)
        assertNull(captor.firstValue.notifId)
        assertNull(captor.firstValue.notifType)
        assertEquals("{}", captor.firstValue.params)
    }
}
