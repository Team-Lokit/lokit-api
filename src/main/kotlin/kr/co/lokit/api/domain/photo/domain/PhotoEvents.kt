package kr.co.lokit.api.domain.photo.domain

/**
 * 확장 규칙(D1): 필드 추가는 허용, 기본값은 주지 않는다.
 * 기본값을 주면 photoId=0인 이벤트가 컴파일을 통과해 무증상 실패(알림이 영원히 안 감)를 만든다.
 */
data class PhotoCreatedEvent(
    val albumId: Long,
    val coupleId: Long,
    val longitude: Double,
    val latitude: Double,
    val photoId: Long,
    val uploaderUserId: Long,
)

data class PhotoLocationUpdatedEvent(
    val albumId: Long,
    val coupleId: Long,
    val longitude: Double,
    val latitude: Double,
)

data class PhotoDeletedEvent(
    val photoUrl: String,
)
