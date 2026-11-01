package kr.co.lokit.api.domain.photo.domain

import kr.co.lokit.api.domain.couple.domain.Couple

/**
 * 조회 이벤트의 뷰어 역할. 항상 "사진 업로더" 기준이다 —
 * comment_view 에서도 댓글 작성자가 아니라 사진 업로더 기준임에 주의.
 */
enum class PhotoViewerRole {
    OWNER,
    PARTNER,
    OTHER,
    ;

    companion object {
        fun of(
            uploadedById: Long,
            viewerUserId: Long,
            couple: Couple?,
        ): PhotoViewerRole =
            when {
                viewerUserId == uploadedById -> OWNER
                couple != null && couple.arePartners(viewerUserId, uploadedById) -> PARTNER
                else -> OTHER
            }
    }
}
