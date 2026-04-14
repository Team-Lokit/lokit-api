package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "apple")
data class AppleOAuthProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val frontRedirectUri: String,
) {
    companion object {
        const val AUTHORIZATION_URL = "https://appleid.apple.com/auth/authorize"
        const val TOKEN_URL = "https://appleid.apple.com/auth/token"
    }
}
