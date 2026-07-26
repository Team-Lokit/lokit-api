package kr.co.lokit.api.domain.photo.domain

object DeIdentifiedUserProfile {
    const val DISPLAY_NAME = "삭제된 사용자"

    fun hiddenProfileImageUrl(): String? = null
}
