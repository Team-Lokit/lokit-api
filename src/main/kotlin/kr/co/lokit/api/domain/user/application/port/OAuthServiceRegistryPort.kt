package kr.co.lokit.api.domain.user.application.port

import kr.co.lokit.api.domain.user.application.LoginService

interface OAuthServiceRegistryPort {
    fun getService(provider: OAuthProvider): LoginService
}
