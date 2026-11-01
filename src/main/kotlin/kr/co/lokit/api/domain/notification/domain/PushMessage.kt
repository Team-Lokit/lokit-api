package kr.co.lokit.api.domain.notification.domain

data class PushMessage(
    val tokens: List<String>,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) { "푸시 제목은 필수입니다." }
        require(body.isNotBlank()) { "푸시 본문은 필수입니다." }
    }
}

/**
 * 부분 실패 허용(D3). invalidTokens 에는 FCM 이 404 + UNREGISTERED 로 응답한 토큰만 담긴다(그 외 404·400 은 failedTokens).
 * NotificationDispatchService 가 발송 직후 이 토큰들을 device_token 에서 물리 삭제한다.
 */
data class PushSendResult(
    val successTokens: List<String> = emptyList(),
    val failedTokens: List<String> = emptyList(),
    val invalidTokens: List<String> = emptyList(),
) {
    val successCount: Int get() = successTokens.size
    val failureCount: Int get() = failedTokens.size + invalidTokens.size

    companion object {
        val EMPTY: PushSendResult = PushSendResult()
    }
}
