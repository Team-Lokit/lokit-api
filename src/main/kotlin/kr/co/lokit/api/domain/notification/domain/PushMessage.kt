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

/** 부분 실패 허용(D3). invalidTokens는 UNREGISTERED 등 '이제 무효' 판정 — 수집·로깅만, 삭제 안 함(G-A). */
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
