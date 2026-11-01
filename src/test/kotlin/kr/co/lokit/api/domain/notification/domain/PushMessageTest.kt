package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * 커버리지 리뷰(슬라이스8)로 신설: `PushMessage`는 지금까지 FCM 어댑터 테스트에서
 * 우연히만 거쳐 갔을 뿐 자체 불변식(제목/본문 공백 금지)을 직접 못박는 테스트가 없었다.
 */
class PushMessageTest {
    @Test
    fun `토큰과 제목과 본문으로 푸시 메시지를 만들 수 있다`() {
        val message = PushMessage(tokens = listOf("token-1"), title = "제목", body = "본문")

        assertEquals(listOf("token-1"), message.tokens)
        assertEquals("제목", message.title)
        assertEquals("본문", message.body)
    }

    @Test
    fun `제목이 공백이면 만들 수 없다`() {
        assertThrows<IllegalArgumentException> {
            PushMessage(tokens = listOf("token-1"), title = "   ", body = "본문")
        }
    }

    @Test
    fun `본문이 공백이면 만들 수 없다`() {
        assertThrows<IllegalArgumentException> {
            PushMessage(tokens = listOf("token-1"), title = "제목", body = "   ")
        }
    }
}
