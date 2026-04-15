package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import kr.co.lokit.api.domain.user.application.port.OAuthClientPort
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo
import org.springframework.stereotype.Component

@Component
class AppleOAuthClientAdapter(
    private val appleOAuthClient: AppleOAuthClient,
) : OAuthClientPort {
    override val provider: OAuthProvider
        get() = appleOAuthClient.provider

    override fun getAccessToken(code: String): String = appleOAuthClient.getAccessToken(code)

    override fun getUserInfo(accessToken: String): OAuthUserInfo = appleOAuthClient.getUserInfo(accessToken)
}
