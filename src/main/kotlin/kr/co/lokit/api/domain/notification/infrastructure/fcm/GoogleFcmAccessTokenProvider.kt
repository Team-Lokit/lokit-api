package kr.co.lokit.api.domain.notification.infrastructure.fcm

import com.google.auth.oauth2.GoogleCredentials
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["notification.push.enabled"], havingValue = "true", matchIfMissing = false)
class GoogleFcmAccessTokenProvider(
    private val properties: FcmProperties,
) : FcmAccessTokenProvider {
    private val credentials: GoogleCredentials by lazy {
        DefaultResourceLoader().getResource(properties.credentialsLocation).inputStream.use {
            GoogleCredentials.fromStream(it).createScoped(FcmProperties.MESSAGING_SCOPE)
        }
    }

    override fun accessToken(): String {
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }
}
