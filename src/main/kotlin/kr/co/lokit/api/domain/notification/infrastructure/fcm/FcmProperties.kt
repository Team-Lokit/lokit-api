package kr.co.lokit.api.domain.notification.infrastructure.fcm

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification.push.fcm")
data class FcmProperties(
    val projectId: String = "",
    val credentialsLocation: String = "",
    val connectTimeoutMillis: Int = 5_000,
    val readTimeoutMillis: Int = 10_000,
    /** 일시 오류(5xx·429·네트워크·401) 시 토큰 1건당 최대 시도 횟수(첫 시도 포함). 액세스 토큰 발급에도 같은 값을 쓴다. */
    val retryMaxAttempts: Int = 3,
    /** 재시도 대기 기본값. n번째 재시도는 retryBackoffMillis * 2^(n-1) 만큼 기다린다. */
    val retryBackoffMillis: Long = 300,
) {
    fun sendUrl(): String = "$FCM_BASE_URL/v1/projects/$projectId/messages:send"

    companion object {
        const val FCM_BASE_URL: String = "https://fcm.googleapis.com"
        const val MESSAGING_SCOPE: String = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
