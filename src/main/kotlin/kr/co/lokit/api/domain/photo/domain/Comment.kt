package kr.co.lokit.api.domain.photo.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class Comment(
    val id: Long = 0L,
    val photoId: Long,
    val userId: Long,
    val content: String,
    val commentedAt: LocalDate = LocalDate.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
) {
    init {
        require(content.isNotBlank()) { "댓글 내용은 필수입니다." }
        require(content.length <= MAX_CONTENT_LENGTH) { "댓글은 ${MAX_CONTENT_LENGTH}자 이내여야 합니다." }
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 500
    }
}
