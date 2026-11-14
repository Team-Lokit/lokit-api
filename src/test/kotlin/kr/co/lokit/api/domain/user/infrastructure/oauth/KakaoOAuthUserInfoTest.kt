package kr.co.lokit.api.domain.user.infrastructure.oauth

import kr.co.lokit.api.domain.user.dto.KakaoUserInfoResponse
import kr.co.lokit.api.domain.user.infrastructure.oauth.kakao.KakaoOAuthUserInfo
import org.junit.jupiter.api.Test
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KakaoOAuthUserInfoTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    private fun userInfo(json: String) = KakaoOAuthUserInfo(mapper.readValue(json, KakaoUserInfoResponse::class.java))

    @Test
    fun `카카오 계정의 이름을 사용한다`() {
        val info = userInfo("""{"id":1,"kakao_account":{"email":"a@b.com","name":"김승진","profile":{"nickname":"승진이"}}}""")

        assertEquals("김승진", info.name)
    }

    @Test
    fun `이름 동의 항목이 없으면 프로필 닉네임을 사용한다`() {
        val info = userInfo("""{"id":1,"kakao_account":{"email":"a@b.com","profile":{"nickname":"승진이"}}}""")

        assertEquals("승진이", info.name)
    }

    @Test
    fun `이름과 프로필 닉네임이 모두 없으면 null이다`() {
        assertNull(userInfo("""{"id":1,"kakao_account":{"email":"a@b.com"}}""").name)
        assertNull(userInfo("""{"id":1}""").name)
    }
}
