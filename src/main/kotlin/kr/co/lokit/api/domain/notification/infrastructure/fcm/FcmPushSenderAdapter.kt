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
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper

/**
 * FCM HTTP v1은 토큰 1건씩만 전송(D3). 어떤 경우에도 예외를 던지지 않고 PushSendResult로 반환.
 *
 * 응답 분류:
 * - 2xx → 성공
 * - 404 + error.details[].errorCode == "UNREGISTERED" → invalidTokens(삭제 대상). 재시도 없음.
 * - 401 → 액세스 토큰 무효화 후 재시도
 * - 429·5xx·네트워크 오류(타임아웃 등) → 지수 백오프 재시도. 소진하면 failedTokens.
 * - 그 외 4xx(400 INVALID_ARGUMENT, 403, UNREGISTERED 아닌 404 …) → 재시도해도 같으므로 즉시 failedTokens.
 * 액세스 토큰 발급 실패도 같은 횟수만큼 재시도하고, 소진하면 전체를 failedTokens 로 반환한다.
 *
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

    private val maxAttempts: Int get() = properties.retryMaxAttempts.coerceAtLeast(1)

    override fun send(message: PushMessage): PushSendResult {
        if (message.tokens.isEmpty()) return PushSendResult.EMPTY
        var accessToken =
            obtainAccessToken() ?: run {
                log.error("FCM 액세스 토큰 발급 최종 실패 — 전체 발송 생략: tokens={}", message.tokens.size)
                return PushSendResult(failedTokens = message.tokens)
            }

        val success = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        message.tokens.forEach { token ->
            val (outcome, latestAccessToken) = sendWithRetry(token, message, accessToken)
            accessToken = latestAccessToken
            when (outcome) {
                SendOutcome.SUCCESS -> success.add(token)
                SendOutcome.INVALID_TOKEN -> invalid.add(token)
                SendOutcome.FAILED -> failed.add(token)
            }
        }
        return PushSendResult(successTokens = success, failedTokens = failed, invalidTokens = invalid)
    }

    private fun obtainAccessToken(): String? {
        for (attempt in 1..maxAttempts) {
            try {
                return accessTokenProvider.accessToken()
            } catch (e: Exception) {
                log.warn("FCM 액세스 토큰 발급 실패 — 시도 {}/{}", attempt, maxAttempts, e)
                if (attempt < maxAttempts) sleepBackoff(attempt)
            }
        }
        return null
    }

    /** 반환값의 두 번째 원소는 401 재발급으로 바뀌었을 수 있는 최신 액세스 토큰이다(다음 토큰 발송에 재사용). */
    private fun sendWithRetry(
        token: String,
        message: PushMessage,
        initialAccessToken: String,
    ): Pair<SendOutcome, String> {
        var accessToken = initialAccessToken
        val tokenSuffix = token.takeLast(TOKEN_LOG_SUFFIX_LENGTH)
        for (attempt in 1..maxAttempts) {
            val result = sendSingle(token, message, accessToken)
            when (result) {
                is SingleResult.Success -> return SendOutcome.SUCCESS to accessToken
                is SingleResult.Invalid -> {
                    log.info("FCM 무효 토큰(UNREGISTERED): tokenSuffix={}", tokenSuffix)
                    return SendOutcome.INVALID_TOKEN to accessToken
                }
                is SingleResult.Permanent -> {
                    log.warn(
                        "FCM 발송 실패(재시도 불가): tokenSuffix={}, status={}, reason={}",
                        tokenSuffix,
                        result.status,
                        result.reason,
                    )
                    return SendOutcome.FAILED to accessToken
                }
                is SingleResult.Retryable -> {
                    if (attempt == maxAttempts) {
                        log.error(
                            "FCM 발송 최종 실패 — 재시도 소진: tokenSuffix={}, attempts={}, status={}, reason={}",
                            tokenSuffix,
                            attempt,
                            result.status,
                            result.reason,
                            result.cause,
                        )
                        return SendOutcome.FAILED to accessToken
                    }
                    log.warn(
                        "FCM 발송 일시 실패 — 재시도 {}/{}: tokenSuffix={}, status={}, reason={}",
                        attempt,
                        maxAttempts,
                        tokenSuffix,
                        result.status,
                        result.reason,
                    )
                    if (result.status == HttpStatus.UNAUTHORIZED.value()) {
                        accessTokenProvider.invalidate()
                        accessToken = obtainAccessToken() ?: run {
                            log.error("FCM 401 후 액세스 토큰 재발급 실패: tokenSuffix={}", tokenSuffix)
                            return SendOutcome.FAILED to accessToken
                        }
                    } else {
                        sleepBackoff(attempt)
                    }
                }
            }
        }
        return SendOutcome.FAILED to accessToken
    }

    private fun sendSingle(token: String, message: PushMessage, accessToken: String): SingleResult =
        try {
            restClient.post()
                .uri(properties.sendUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(token, message))
                .retrieve()
                .toBodilessEntity()
            SingleResult.Success
        } catch (e: RestClientResponseException) {
            classify(e)
        } catch (e: ResourceAccessException) {
            SingleResult.Retryable(status = null, reason = e.message ?: "network error", cause = e)
        } catch (e: Exception) {
            SingleResult.Permanent(status = null, reason = "${e.javaClass.simpleName}: ${e.message}")
        }

    private fun classify(e: RestClientResponseException): SingleResult {
        val status = e.statusCode.value()
        val body = e.responseBodyAsString.take(ERROR_BODY_LOG_LENGTH)
        return when {
            status == HttpStatus.NOT_FOUND.value() && isUnregistered(e.responseBodyAsString) -> SingleResult.Invalid
            status == HttpStatus.UNAUTHORIZED.value() ||
                status == HttpStatus.TOO_MANY_REQUESTS.value() ||
                e.statusCode.is5xxServerError -> SingleResult.Retryable(status, body, e)
            else -> SingleResult.Permanent(status, body)
        }
    }

    private fun sleepBackoff(attempt: Int) {
        val delay = properties.retryBackoffMillis * (1L shl (attempt - 1))
        if (delay <= 0) return
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // D1: 404 + error.details[].errorCode == "UNREGISTERED" 만 무효 토큰. 파싱 실패는 무효 아님.
    private fun isUnregistered(body: String): Boolean =
        runCatching {
            JSON_MAPPER.readTree(body).path("error").path("details")
                .any { it.path("errorCode").asString() == UNREGISTERED }
        }.getOrDefault(false)

    private fun requestBody(token: String, message: PushMessage): Map<String, Any> =
        mapOf(
            "message" to
                mapOf(
                    "token" to token,
                    "notification" to mapOf("title" to message.title, "body" to message.body),
                    "data" to message.data,
                ),
        )

    private enum class SendOutcome { SUCCESS, INVALID_TOKEN, FAILED }

    private sealed interface SingleResult {
        data object Success : SingleResult

        data object Invalid : SingleResult

        data class Retryable(val status: Int?, val reason: String, val cause: Exception) : SingleResult

        data class Permanent(val status: Int?, val reason: String) : SingleResult
    }

    companion object {
        private const val TOKEN_LOG_SUFFIX_LENGTH = 8
        private const val ERROR_BODY_LOG_LENGTH = 300
        private const val UNREGISTERED = "UNREGISTERED"
        private val JSON_MAPPER = JsonMapper.builder().build()
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
