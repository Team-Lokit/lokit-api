package kr.co.lokit.api.domain.couple.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoupleTest {
    @Test
    fun `정상적으로 커플을 생성할 수 있다`() {
        val couple = Couple(name = "우리 커플")

        assertEquals("우리 커플", couple.name)
        assertEquals(0L, couple.id)
    }

    @Test
    fun `기본값이 올바르게 설정된다`() {
        val couple = Couple(name = "테스트")

        assertEquals(0L, couple.id)
        assertEquals(emptyList(), couple.userIds)
    }

    @Test
    fun `모든 필드를 지정하여 커플을 생성할 수 있다`() {
        val couple =
            Couple(
                id = 1L,
                name = "우리 커플",
                userIds = listOf(1L, 2L),
            )

        assertEquals(1L, couple.id)
        assertEquals("우리 커플", couple.name)
        assertEquals(listOf(1L, 2L), couple.userIds)
    }

    @Test
    fun `두 사용자가 모두 멤버면 파트너 관계다`() {
        val couple = Couple(id = 1L, name = "우리 커플", userIds = listOf(1L, 2L))

        assertTrue(couple.isMember(1L))
        assertTrue(couple.isMember(2L))
        assertTrue(couple.arePartners(1L, 2L))
    }

    @Test
    fun `비멤버가 섞이면 파트너가 아니다`() {
        // partnerIdFor(99) 는 userIds[0] 인 1L 을 돌려주는 함정이 있다(계약 0-R-2).
        // arePartners 는 양쪽 모두의 멤버십을 검사해 그 거짓 양성을 차단해야 한다.
        val couple = Couple(id = 1L, name = "우리 커플", userIds = listOf(1L, 2L))

        assertFalse(couple.isMember(99L))
        assertFalse(couple.arePartners(99L, 1L))
        assertFalse(couple.arePartners(1L, 99L))
    }

    @Test
    fun `같은 사용자는 자기 자신의 파트너가 아니다`() {
        val couple = Couple(id = 1L, name = "우리 커플", userIds = listOf(1L, 2L))

        assertFalse(couple.arePartners(1L, 1L))
    }
}
