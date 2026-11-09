package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * 애플은 이름을 id_token 에 담지 않고, 최초 인증 시 콜백의 user 폼 파라미터(JSON)로만 한 번 보낸다.
 * 서명되지 않은 값이고 닉네임 기본값으로만 쓰므로, 형식이 틀려도 로그인을 막지 않고 null 을 돌려준다.
 */
object AppleUserNameParser {
    private val mapper = JsonMapper.builder().build()
    private val KOREAN = Regex("[\\p{IsHangul}]")

    fun parse(user: String?): String? {
        if (user.isNullOrBlank()) return null
        val name =
            try {
                mapper.readTree(user).path("name")
            } catch (_: JacksonException) {
                return null
            }
        return fullName(
            firstName = name.path("firstName").asString("").trim(),
            lastName = name.path("lastName").asString("").trim(),
        ).ifBlank { null }
    }

    private fun fullName(
        firstName: String,
        lastName: String,
    ): String =
        if (KOREAN.containsMatchIn(firstName + lastName)) {
            lastName + firstName
        } else {
            listOf(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        }
}
