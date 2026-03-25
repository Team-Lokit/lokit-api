package kr.co.lokit.api.domain.couple.domain

enum class CoupleDisconnectAction {
    DISCONNECT_AND_REMOVE,
    REMOVE_MEMBER_ONLY,
    ALREADY_DISCONNECTED_BY_REQUESTER,
}

enum class CoupleReconnectRejection {
    NOT_DISCONNECTED,
    RECONNECT_WINDOW_EXPIRED,
    NO_REMAINING_MEMBER,
}

enum class CoupleReconnectRejectReason(
    val code: String,
) {
    NO_REMAINING_MEMBER("no_remaining_member"),
}
