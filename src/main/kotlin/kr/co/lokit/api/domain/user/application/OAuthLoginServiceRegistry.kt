package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthServiceRegistryPort
import org.springframework.stereotype.Component

@Component
class OAuthLoginServiceRegistry(
    services: List<LoginService>,
) : OAuthServiceRegistryPort {
    private val clientMap: Map<OAuthProvider, LoginService> =
        services.associateBy { it.provider }

    override fun getService(provider: OAuthProvider): LoginService =
        clientMap[provider]
            ?: throw IllegalArgumentException("지원하지 않는 OAuth 서비스입니다: $provider")
}
