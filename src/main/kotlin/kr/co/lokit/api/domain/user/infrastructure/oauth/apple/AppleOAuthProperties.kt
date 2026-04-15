package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apple")
data class AppleOAuthProperties(
    val clientId: String,
    val teamId: String,
    val keyId: String,
    val privateKey: String,
    val redirectUri: String,
    val frontRedirectUri: String,
) {
    companion object {
        const val AUTHORIZATION_URL = "https://appleid.apple.com/auth/authorize"
        const val TOKEN_URL = "https://appleid.apple.com/auth/token"
        const val PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys"
    }
}
