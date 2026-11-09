package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.domain.LoginResult

interface LoginService {
    val provider: OAuthProvider

    /**
     * @param providedName 토큰이 아닌 인증 응답으로 전달된 이름. 신규 가입자의 기본 닉네임에만 쓰인다.
     */
    fun login(
        code: String,
        providedName: String? = null,
    ): LoginResult
}
