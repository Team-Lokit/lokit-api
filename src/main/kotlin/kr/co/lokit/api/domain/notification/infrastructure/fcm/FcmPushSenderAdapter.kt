package kr.co.lokit.api.domain.notification.infrastructure.fcm

import kr.co.lokit.api.domain.notification.application.port.PushSenderPort
import kr.co.lokit.api.domain.notification.domain.PushMessage
import kr.co.lokit.api.domain.notification.domain.PushSendResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * FCM HTTP v1은 토큰 1건씩만 전송(D3). 어떤 경우에도 예외를 던지지 않고 PushSendResult로 반환.
 * restClient를 기본값 파라미터로 둔 이유(F14): 컨텍스트에 RestClient 빈이 없어 안전.
 * 테스트: RestClient.builder() + MockRestServiceServer.bindTo(builder) 로 실제 네트워크 없이 검증.
 */
@Component
@ConditionalOnProperty(name = ["notification.push.enabled"], havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(FcmProperties::class)
class FcmPushSenderAdapter(
    private val properties: FcmProperties,
    private val accessTokenProvider: FcmAccessTokenProvider,
    private val restClient: RestClient = defaultRestClient(properties),
) : PushSenderPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: PushMessage): PushSendResult {
        if (message.tokens.isEmpty()) return PushSendResult.EMPTY
        val accessToken =
            runCatching { accessTokenProvider.accessToken() }
                .getOrElse { e ->
                    log.warn("FCM 액세스 토큰 발급 실패 — 전체 발송 생략", e)
                    return PushSendResult(failedTokens = message.tokens)
                }

        val success = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        message.tokens.forEach { token ->
            when (sendSingle(token, message, accessToken)) {
                SendOutcome.SUCCESS -> success.add(token)
                SendOutcome.INVALID_TOKEN -> invalid.add(token)
                SendOutcome.RETRYABLE_FAILURE -> failed.add(token)
            }
        }
        return PushSendResult(successTokens = success, failedTokens = failed, invalidTokens = invalid)
    }

    private fun sendSingle(token: String, message: PushMessage, accessToken: String): SendOutcome =
        try {
            restClient.post()
                .uri(properties.sendUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(token, message))
                .retrieve()
                .onStatus({ it == HttpStatus.NOT_FOUND || it == HttpStatus.BAD_REQUEST }) { _, _ ->
                    throw InvalidTokenException()
                }
                .toBodilessEntity()
            SendOutcome.SUCCESS
        } catch (e: InvalidTokenException) {
            SendOutcome.INVALID_TOKEN
        } catch (e: Exception) {
            log.warn("FCM 발송 실패: tokenSuffix={}", token.takeLast(TOKEN_LOG_SUFFIX_LENGTH), e)
            SendOutcome.RETRYABLE_FAILURE
        }

    private fun requestBody(token: String, message: PushMessage): Map<String, Any> =
        mapOf(
            "message" to
                mapOf(
                    "token" to token,
                    "notification" to mapOf("title" to message.title, "body" to message.body),
                    "data" to message.data,
                ),
        )

    private enum class SendOutcome { SUCCESS, INVALID_TOKEN, RETRYABLE_FAILURE }

    private class InvalidTokenException : RuntimeException()

    companion object {
        private const val TOKEN_LOG_SUFFIX_LENGTH = 8
    }
}

/**
 * 생성자 기본값 표현식은 앞선 파라미터(properties)를 참조할 수 있다.
 * companion 의 정적 팩토리로 두면 인스턴스 프로퍼티에 닿지 못해
 * 타임아웃이 하드코딩으로 굳는다 — 그래서 최상위 함수로 두고 properties 를 받는다.
 */
private fun defaultRestClient(properties: FcmProperties): RestClient =
    RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeoutMillis)
                setReadTimeout(properties.readTimeoutMillis)
            },
        )
        .build()
