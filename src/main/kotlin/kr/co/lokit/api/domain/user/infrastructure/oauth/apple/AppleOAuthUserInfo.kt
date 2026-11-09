package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo

class AppleOAuthUserInfo(
    override val providerId: String,
    override val email: String?,
) : OAuthUserInfo {
    override val provider: OAuthProvider = OAuthProvider.APPLE

    // 애플 id_token 에는 이름이 없다. 콜백의 user 파라미터로 따로 받는다(AppleUserNameParser).
    override val name: String? = null
}
