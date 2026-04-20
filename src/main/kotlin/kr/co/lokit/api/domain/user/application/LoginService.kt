package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.domain.LoginResult

interface LoginService {
    val provider: OAuthProvider

    fun login(code: String): LoginResult
}
