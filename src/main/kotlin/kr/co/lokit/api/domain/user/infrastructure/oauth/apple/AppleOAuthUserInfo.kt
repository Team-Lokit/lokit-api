package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo

class AppleOAuthUserInfo(
    override val providerId: String,
    override val email: String?,
) : OAuthUserInfo {
    override val provider: OAuthProvider = OAuthProvider.APPLE
}
