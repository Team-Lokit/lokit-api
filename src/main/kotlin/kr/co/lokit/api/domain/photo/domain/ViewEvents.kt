package kr.co.lokit.api.domain.photo.domain

/**
 * 조회(view) 이벤트. 핸들러가 재조회를 하지 않도록 필요한 값을 전부 들고 다닌다(계약 §3.3).
 * viewerRole 은 항상 "사진 업로더" 기준이다(PhotoViewerRole 참고).
 */
data class PhotoViewedEvent(
    val photoId: Long,
    val viewerUserId: Long,
    val photoOwnerId: Long,
    val viewerRole: PhotoViewerRole,
)

data class CommentListViewedEvent(
    val photoId: Long,
    val viewerUserId: Long,
    val photoOwnerId: Long,
    val viewerRole: PhotoViewerRole,
    val commentCount: Int,
)
