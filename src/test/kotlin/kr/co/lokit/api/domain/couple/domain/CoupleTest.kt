package kr.co.lokit.api.domain.couple.domain

import kr.co.lokit.api.common.constants.CoupleStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    @Test
    fun `재연결 가능 상태면 재연결 마감일은 끊긴 날짜 + 31일이다`() {
        val now = LocalDateTime.of(2026, 1, 20, 12, 0)
        val couple =
            Couple(
                name = "c",
                userIds = listOf(2L),
                status = CoupleStatus.DISCONNECTED,
                disconnectedAt = LocalDateTime.of(2026, 1, 11, 9, 0),
                disconnectedByUserId = 1L,
            )

        assertEquals(LocalDate.of(2026, 2, 11), couple.reconnectDeadline(now))
    }

    @Test
    fun `연결 중이거나 재연결 기간이 지났거나 남은 멤버가 없으면 재연결 마감일은 null이다`() {
        val disconnectedAt = LocalDateTime.of(2026, 1, 11, 9, 0)
        val base =
            Couple(
                name = "c",
                userIds = listOf(2L),
                status = CoupleStatus.DISCONNECTED,
                disconnectedAt = disconnectedAt,
                disconnectedByUserId = 1L,
            )

        assertNull(Couple(name = "c", userIds = listOf(1L, 2L)).reconnectDeadline())
        assertNull(base.reconnectDeadline(disconnectedAt.plusDays(32)))
        assertNull(base.copy(status = CoupleStatus.EXPIRED).reconnectDeadline(disconnectedAt.plusDays(1)))
        assertNull(base.copy(userIds = emptyList()).reconnectDeadline(disconnectedAt.plusDays(1)))
    }

    @Test
    fun `연결 끊김 알림창은 상대가 끊은 경우 남은 멤버에게 확인 전까지만 보인다`() {
        val couple =
            Couple(
                name = "c",
                userIds = listOf(2L),
                status = CoupleStatus.DISCONNECTED,
                disconnectedAt = LocalDateTime.now(),
                disconnectedByUserId = 1L,
            )

        assertTrue(couple.shouldShowPartnerDisconnectNoticeTo(2L))
        assertTrue(couple.copy(status = CoupleStatus.EXPIRED).shouldShowPartnerDisconnectNoticeTo(2L))
        assertFalse(couple.shouldShowPartnerDisconnectNoticeTo(1L))
        assertFalse(couple.shouldShowPartnerDisconnectNoticeTo(3L))
        assertFalse(couple.copy(disconnectNoticeConfirmedAt = LocalDateTime.now()).shouldShowPartnerDisconnectNoticeTo(2L))
        assertFalse(Couple(name = "c", userIds = listOf(1L, 2L)).shouldShowPartnerDisconnectNoticeTo(2L))
    }
}
