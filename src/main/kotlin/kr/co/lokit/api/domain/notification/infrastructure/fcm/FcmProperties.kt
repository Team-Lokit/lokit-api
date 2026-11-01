package kr.co.lokit.api.domain.notification.infrastructure.fcm

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification.push.fcm")
data class FcmProperties(
    val projectId: String = "",
    val credentialsLocation: String = "",
    val connectTimeoutMillis: Int = 5_000,
    val readTimeoutMillis: Int = 10_000,
) {
    fun sendUrl(): String = "$FCM_BASE_URL/v1/projects/$projectId/messages:send"

    companion object {
        const val FCM_BASE_URL: String = "https://fcm.googleapis.com"
        const val MESSAGING_SCOPE: String = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
