package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import kr.co.lokit.api.domain.user.application.port.OAuthClientPort
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo

class AppleOAuthClientAdapter(private val appleOAuthClient: AppleOAuthClient): OAuthClientPort {
    override val provider: OAuthProvider = OAuthProvider.APPLE

    override fun getAccessToken(code: String): String {
        TODO("Not yet implemented")
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        TODO("Not yet implemented")
    }
}
