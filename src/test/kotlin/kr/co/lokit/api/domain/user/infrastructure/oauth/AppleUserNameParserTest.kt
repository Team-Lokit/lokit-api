package kr.co.lokit.api.domain.user.infrastructure.oauth

import kr.co.lokit.api.domain.user.infrastructure.oauth.apple.AppleUserNameParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleUserNameParserTest {
    private fun payload(
        firstName: String?,
        lastName: String?,
    ): String {
        val fields =
            listOfNotNull(
                firstName?.let { """"firstName":"$it"""" },
                lastName?.let { """"lastName":"$it"""" },
            ).joinToString(",")
        return """{"name":{$fields},"email":"a@privaterelay.appleid.com"}"""
    }

    @Test
    fun `한글 이름은 성과 이름을 공백 없이 붙인다`() {
        assertEquals("김승진", AppleUserNameParser.parse(payload("승진", "김")))
    }

    @Test
    fun `영문 이름은 이름 성 순서로 공백을 두고 붙인다`() {
        assertEquals("Jane Doe", AppleUserNameParser.parse(payload("Jane", "Doe")))
    }

    @Test
    fun `이름이나 성 중 하나만 있으면 있는 값만 사용한다`() {
        assertEquals("Jane", AppleUserNameParser.parse(payload("Jane", null)))
        assertEquals("김", AppleUserNameParser.parse(payload(null, "김")))
    }

    @Test
    fun `user 파라미터가 없으면 null이다`() {
        assertNull(AppleUserNameParser.parse(null))
    }

    @Test
    fun `이름 정보가 없거나 비어 있으면 null이다`() {
        assertNull(AppleUserNameParser.parse("""{"email":"a@b.com"}"""))
        assertNull(AppleUserNameParser.parse(payload(" ", "")))
    }

    @Test
    fun `형식이 잘못된 값이어도 예외 없이 null이다`() {
        assertNull(AppleUserNameParser.parse("not-json{"))
        assertNull(AppleUserNameParser.parse("[1,2]"))
    }
}
