package kr.co.lokit.api.domain.user.application.port

interface OAuthUserInfo {
    val provider: OAuthProvider
    val providerId: String
    val email: String?
    val name: String?
}
