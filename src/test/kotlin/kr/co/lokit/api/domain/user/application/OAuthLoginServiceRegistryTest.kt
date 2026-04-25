package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.domain.LoginResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertSame

class OAuthLoginServiceRegistryTest {
    private class FakeLoginService(
        override val provider: OAuthProvider,
    ) : LoginService {
        override fun login(code: String): LoginResult = throw UnsupportedOperationException("not used in registry test")
    }

    @Test
    fun `등록된 provider의 서비스를 반환한다`() {
        val kakao = FakeLoginService(OAuthProvider.KAKAO)
        val apple = FakeLoginService(OAuthProvider.APPLE)
        val registry = OAuthLoginServiceRegistry(listOf(kakao, apple))

        assertSame(kakao, registry.getService(OAuthProvider.KAKAO))
        assertSame(apple, registry.getService(OAuthProvider.APPLE))
    }

    @Test
    fun `등록되지 않은 provider를 조회하면 IllegalArgumentException이 발생한다`() {
        val registry = OAuthLoginServiceRegistry(listOf(FakeLoginService(OAuthProvider.KAKAO)))

        assertThrows<IllegalArgumentException> {
            registry.getService(OAuthProvider.APPLE)
        }
    }

    @Test
    fun `LoginService가 하나도 없으면 모든 provider 조회가 실패한다`() {
        val registry = OAuthLoginServiceRegistry(emptyList())

        assertThrows<IllegalArgumentException> {
            registry.getService(OAuthProvider.KAKAO)
        }
        assertThrows<IllegalArgumentException> {
            registry.getService(OAuthProvider.APPLE)
        }
    }
}
