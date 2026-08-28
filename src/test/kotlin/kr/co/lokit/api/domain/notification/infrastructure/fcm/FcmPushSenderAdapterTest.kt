package kr.co.lokit.api.domain.notification.infrastructure.fcm

import kr.co.lokit.api.domain.notification.domain.PushMessage
import kr.co.lokit.api.domain.notification.domain.PushSendResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 슬라이스 없음(T6). @RestClientTest 는 어댑터가 RestClient 를 자체 생성하는 구조(F14)와 맞지 않고,
 * 이 테스트가 검증하려는 것(요청 모양·상태코드별 분류)에 스프링 컨텍스트가 전혀 필요 없다.
 * MockRestServiceServer.bindTo(RestClient.Builder) 로 실제 네트워크 없이 요청을 실측한다(F16).
 *
 * B13(D3) 계약을 통째로 못 박는다: 기기별 독립 발송, 부분 실패 허용, **어떤 경우에도 예외 미전파**.
 * 예외 미전파는 별도 어서션이 아니라 "send() 가 반환한다"는 사실 자체로 증명된다 —
 * 예외가 새면 테스트는 assertion-red 가 아니라 error 로 죽는다.
 */
class FcmPushSenderAdapterTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var accessTokenProvider: FcmAccessTokenProvider
    private lateinit var adapter: FcmPushSenderAdapter

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        // bindTo(builder).build() 가 builder 에 목 요청팩토리를 심는다. 반드시 builder.build() 보다 먼저.
        server = MockRestServiceServer.bindTo(builder).build()
        accessTokenProvider = mock()
        adapter =
            FcmPushSenderAdapter(
                properties = FcmProperties(
                    projectId = PROJECT_ID,
                    credentialsLocation = "",
                    connectTimeoutMillis = 5_000,
                    readTimeoutMillis = 10_000,
                ),
                accessTokenProvider = accessTokenProvider,
                restClient = builder.build(),
            )
    }

    @Test
    fun `토큰이 없으면 아무 요청도 보내지 않는다`() {
        val result = adapter.send(PushMessage(tokens = emptyList(), title = TITLE, body = BODY))

        assertEquals(PushSendResult.EMPTY, result)
        // 기대 요청을 하나도 등록하지 않았으므로 요청이 1건이라도 나갔다면 여기서 깨진다.
        server.verify()
    }

    /**
     * 🔴 핵심. FCM HTTP v1 은 토큰 1건씩만 받는다(legacy batch 폐기) → 토큰 수만큼 POST 가 나가야 한다.
     * URL 리터럴을 그대로 적어 FcmProperties.sendUrl() 조립까지 함께 못 박는다.
     */
    @Test
    fun `토큰마다 FCM v1 메시지 전송 요청을 보낸다`() {
        whenever(accessTokenProvider.accessToken()).thenReturn(ACCESS_TOKEN)
        server.expect(times(2), requestTo(SEND_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $ACCESS_TOKEN"))
            .andExpect(jsonPath("$.message.token").exists())
            .andExpect(jsonPath("$.message.notification.title").value(TITLE))
            .andExpect(jsonPath("$.message.notification.body").value(BODY))
            .andRespond(withSuccess())

        val result = adapter.send(PushMessage(tokens = listOf(TOKEN_1, TOKEN_2), title = TITLE, body = BODY))

        server.verify()
        assertEquals(listOf(TOKEN_1, TOKEN_2), result.successTokens)
    }

    /**
     * 부분 실패 계약(D3): 가운데 토큰이 404 여도 루프가 멈추지 않고 3번째 기기까지 발송한다.
     * 404 기대에 token 값을 박아 "두 번째 토큰이 무효였다"를 요청 순서까지 포함해 고정한다.
     */
    @Test
    fun `404 응답이면 무효 토큰으로 분류하고 나머지 기기 발송은 계속한다`() {
        whenever(accessTokenProvider.accessToken()).thenReturn(ACCESS_TOKEN)
        server.expect(requestTo(SEND_URL))
            .andExpect(jsonPath("$.message.token").value(TOKEN_1))
            .andRespond(withSuccess())
        server.expect(requestTo(SEND_URL))
            .andExpect(jsonPath("$.message.token").value(TOKEN_2))
            .andRespond(withResourceNotFound())
        server.expect(requestTo(SEND_URL))
            .andExpect(jsonPath("$.message.token").value(TOKEN_3))
            .andRespond(withSuccess())

        val result = adapter.send(
            PushMessage(tokens = listOf(TOKEN_1, TOKEN_2, TOKEN_3), title = TITLE, body = BODY),
        )

        server.verify()
        assertEquals(listOf(TOKEN_1, TOKEN_3), result.successTokens)
        assertEquals(listOf(TOKEN_2), result.invalidTokens)
        assertTrue(result.failedTokens.isEmpty(), "404 는 재시도 가능 실패가 아니라 무효 토큰이다.")
    }

    /**
     * 5xx 는 서버 사정이므로 토큰을 무효로 낙인찍으면 안 된다(G-A: 무효 토큰은 수집·로깅 대상).
     * failedTokens 와 invalidTokens 를 갈라 놓는 이유가 바로 이 구분이다.
     */
    @Test
    fun `500 응답이면 재시도 가능 실패로 분류하고 예외를 던지지 않는다`() {
        whenever(accessTokenProvider.accessToken()).thenReturn(ACCESS_TOKEN)
        server.expect(requestTo(SEND_URL)).andRespond(withServerError())

        val result = adapter.send(PushMessage(tokens = listOf(TOKEN_1), title = TITLE, body = BODY))

        server.verify()
        assertEquals(listOf(TOKEN_1), result.failedTokens)
        assertTrue(result.successTokens.isEmpty())
        assertTrue(result.invalidTokens.isEmpty(), "5xx 는 서버 사정이지 토큰이 무효라는 뜻이 아니다.")
    }

    /**
     * 토큰 발급 실패는 @Async 백그라운드에서 터지므로 던져봐야 받아줄 사람이 없다(D3).
     * 전체를 failedTokens 로 표시하고 정상 반환한다. 요청은 1건도 나가면 안 된다.
     */
    @Test
    fun `액세스 토큰 발급이 실패하면 전체를 실패로 표시하고 예외를 던지지 않는다`() {
        whenever(accessTokenProvider.accessToken()).thenThrow(IllegalStateException("서비스 계정 키를 읽을 수 없습니다."))

        val result = adapter.send(
            PushMessage(tokens = listOf(TOKEN_1, TOKEN_2, TOKEN_3), title = TITLE, body = BODY),
        )

        server.verify()
        assertEquals(listOf(TOKEN_1, TOKEN_2, TOKEN_3), result.failedTokens)
        assertTrue(result.successTokens.isEmpty())
        assertTrue(result.invalidTokens.isEmpty())
    }

    companion object {
        private const val PROJECT_ID = "test-project"
        private const val SEND_URL = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"
        private const val ACCESS_TOKEN = "test-access-token"
        private const val TITLE = "제목"
        private const val BODY = "본문"
        private const val TOKEN_1 = "device-token-1"
        private const val TOKEN_2 = "device-token-2"
        private const val TOKEN_3 = "device-token-3"
    }
}
