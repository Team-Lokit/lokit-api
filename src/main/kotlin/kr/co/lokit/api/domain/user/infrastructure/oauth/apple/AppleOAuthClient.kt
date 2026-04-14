package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo
import kr.co.lokit.api.domain.user.infrastructure.oauth.OAuthClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@EnableConfigurationProperties(AppleOAuthProperties::class)
class AppleOAuthClient(
    private val properties: AppleOAuthProperties,
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5_000)
                    setReadTimeout(10_000)
                },
            ).build(),
): OAuthClient {
    override val provider: OAuthProvider = OAuthProvider.APPLE

    override fun getAccessToken(code: String): String {
        TODO("Not yet implemented")
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        TODO("Not yet implemented")
    }
}
