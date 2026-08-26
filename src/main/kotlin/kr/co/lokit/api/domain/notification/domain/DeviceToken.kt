package kr.co.lokit.api.domain.notification.domain

data class DeviceToken(
    val id: Long = 0L,
    val userId: Long,
    val token: String,
    val platform: DevicePlatform,
) {
    init {
        require(token.isNotBlank()) { "디바이스 토큰은 필수입니다." }
        require(token.length <= MAX_TOKEN_LENGTH) { "디바이스 토큰은 ${MAX_TOKEN_LENGTH}자 이내여야 합니다." }
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 512
    }
}
