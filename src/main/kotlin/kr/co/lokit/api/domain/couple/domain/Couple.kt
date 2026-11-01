package kr.co.lokit.api.domain.couple.domain

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.constants.GracePeriodPolicy
import java.time.LocalDate
import java.time.LocalDateTime

data class Couple(
    val id: Long = 0,
    val name: String,
    val userIds: List<Long> = emptyList(),
    val status: CoupleStatus = CoupleStatus.CONNECTED,
    val disconnectedAt: LocalDateTime? = null,
    val disconnectedByUserId: Long? = null,
    val firstMetDate: LocalDate? = null,
) {
    init {
        require(userIds.size <= MAX_MEMBERS)
    }

    fun isFull(): Boolean = userIds.size >= MAX_MEMBERS

    fun isReconnectWindowExpired(now: LocalDateTime = LocalDateTime.now()): Boolean =
        disconnectedAt
            ?.plusDays(GracePeriodPolicy.RECONNECT_DAYS)
            ?.isBefore(now)
            ?: true

    fun hasRemainingMemberForReconnect(): Boolean = userIds.isNotEmpty()

    fun partnerIdFor(userId: Long): Long? = userIds.firstOrNull { it != userId }

    /** userIds 기반 멤버십. partnerIdFor 와 달리 비멤버에게 거짓 양성을 주지 않는다. */
    fun isMember(userId: Long): Boolean = userIds.contains(userId)

    /**
     * 두 사용자가 이 커플의 서로 다른 두 멤버인가.
     * partnerIdFor(x) == y 로 대체하지 말 것 — 비멤버 x 에 대해 userIds[0] 을 반환하는 함정이 있다.
     * status 는 보지 않는다: 연결 해제 후에도 신원 관계는 유지된다는 것이 이 메서드의 계약이다.
     */
    fun arePartners(
        userIdA: Long,
        userIdB: Long,
    ): Boolean = userIdA != userIdB && isMember(userIdA) && isMember(userIdB)

    fun deIdentifiedUserId(): Long? = disconnectedByUserId.takeIf { status.isDisconnectedOrExpired }

    fun isConnectedAndFull(): Boolean = status == CoupleStatus.CONNECTED && isFull()

    fun disconnectActionFor(userId: Long): CoupleDisconnectAction =
        when {
            !status.isDisconnectedOrExpired -> CoupleDisconnectAction.DISCONNECT_AND_REMOVE
            disconnectedByUserId == userId -> CoupleDisconnectAction.ALREADY_DISCONNECTED_BY_REQUESTER
            else -> CoupleDisconnectAction.REMOVE_MEMBER_ONLY
        }

    fun reconnectRejectionReason(now: LocalDateTime = LocalDateTime.now()): CoupleReconnectRejection? =
        when {
            status != CoupleStatus.DISCONNECTED -> CoupleReconnectRejection.NOT_DISCONNECTED
            disconnectedAt == null -> CoupleReconnectRejection.NOT_DISCONNECTED
            isReconnectWindowExpired(now) -> CoupleReconnectRejection.RECONNECT_WINDOW_EXPIRED
            !hasRemainingMemberForReconnect() -> CoupleReconnectRejection.NO_REMAINING_MEMBER
            else -> null
        }

    companion object {
        const val DEFAULT_COUPLE_NAME = "default"
        const val MAX_MEMBERS = 2
    }
}
