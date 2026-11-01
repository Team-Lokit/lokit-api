package kr.co.lokit.api.fixture

import kr.co.lokit.api.common.constants.AccountStatus
import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.constants.UserRole
import kr.co.lokit.api.domain.album.domain.Album
import kr.co.lokit.api.domain.couple.domain.Couple
import kr.co.lokit.api.domain.map.domain.AlbumBounds
import kr.co.lokit.api.domain.map.domain.BoundsIdType
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.domain.DeviceToken
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import kr.co.lokit.api.domain.photo.domain.Comment
import kr.co.lokit.api.domain.photo.domain.Emoticon
import kr.co.lokit.api.domain.photo.domain.Location
import kr.co.lokit.api.domain.photo.domain.Photo
import kr.co.lokit.api.domain.photo.domain.PhotoDetail
import kr.co.lokit.api.domain.user.domain.User
import java.time.LocalDate
import java.time.LocalDateTime

fun createComment(
    id: Long = 0L,
    photoId: Long = 1L,
    userId: Long = 1L,
    content: String = "테스트 댓글",
    commentedAt: LocalDate = LocalDate.of(2025, 1, 1),
    createdAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0),
    updatedAt: LocalDateTime = createdAt,
) = Comment(
    id = id,
    photoId = photoId,
    userId = userId,
    content = content,
    commentedAt = commentedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun createEmoticon(
    id: Long = 0L,
    commentId: Long = 1L,
    userId: Long = 1L,
    emoji: String = "😀",
) = Emoticon(id = id, commentId = commentId, userId = userId, emoji = emoji)

fun createUser(
    id: Long = 0L,
    email: String = "test@test.com",
    name: String = "테스트",
    role: UserRole = UserRole.USER,
    status: AccountStatus = AccountStatus.ACTIVE,
    withdrawnAt: LocalDateTime? = null,
) = User(id = id, email = email, name = name, role = role, status = status, withdrawnAt = withdrawnAt)

fun createCouple(
    id: Long = 0L,
    name: String = "테스트",
    userIds: List<Long> = emptyList(),
    status: CoupleStatus = CoupleStatus.CONNECTED,
    disconnectedAt: LocalDateTime? = null,
    disconnectedByUserId: Long? = null,
    firstMetDate: LocalDate? = null,
) = Couple(id = id, name = name, userIds = userIds, status = status, disconnectedAt = disconnectedAt, disconnectedByUserId = disconnectedByUserId, firstMetDate = firstMetDate)

fun createAlbum(
    id: Long = 0L,
    title: String = "여행",
    coupleId: Long = 1L,
    createdById: Long = 1L,
    photoCount: Int = 0,
    isDefault: Boolean = false,
) = Album(
    id = id,
    title = title,
    coupleId = coupleId,
    createdById = createdById,
    photoCount = photoCount,
    isDefault = isDefault,
)

fun createPhoto(
    id: Long = 0L,
    albumId: Long = 1L,
    coupleId: Long? = null,
    location: Location = createLocation(),
    description: String? = null,
    url: String = "https://example.com/photo.jpg",
    uploadedById: Long = 1L,
    takenAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 12, 0),
    address: String? = null,
) = Photo(
    id = id,
    albumId = albumId,
    coupleId = coupleId,
    location = location,
    description = description,
    url = url,
    uploadedById = uploadedById,
    takenAt = takenAt,
    address = address,
)

fun createLocation(
    longitude: Double = 127.0,
    latitude: Double = 37.5,
) = Location(longitude = longitude, latitude = latitude)

fun createPhotoDetail(
    id: Long = 1L,
    url: String = "https://example.com/photo.jpg",
    takenAt: LocalDateTime? = LocalDateTime.of(2026, 1, 1, 12, 0),
    albumName: String = "여행",
    uploadedById: Long = 1L,
    uploaderName: String = "테스트",
    uploaderProfileImageUrl: String? = null,
    location: Location = createLocation(),
    description: String? = null,
) = PhotoDetail(
    id = id,
    url = url,
    takenAt = takenAt,
    albumName = albumName,
    uploadedById = uploadedById,
    uploaderName = uploaderName,
    uploaderProfileImageUrl = uploaderProfileImageUrl,
    location = location,
    description = description,
)

fun createAlbumBounds(
    id: Long = 0L,
    albumId: Long = 1L,
    idType: BoundsIdType = BoundsIdType.ALBUM,
    minLongitude: Double = 127.0,
    maxLongitude: Double = 127.0,
    minLatitude: Double = 37.5,
    maxLatitude: Double = 37.5,
) = AlbumBounds(
    id = id,
    standardId = albumId,
    idType = idType,
    minLongitude = minLongitude,
    maxLongitude = maxLongitude,
    minLatitude = minLatitude,
    maxLatitude = maxLatitude,
)

fun createDeviceToken(
    id: Long = 0L,
    userId: Long = 1L,
    token: String = "fcm-token-1",
    platform: DevicePlatform = DevicePlatform.ANDROID,
) = DeviceToken(
    id = id,
    userId = userId,
    token = token,
    platform = platform,
)

/**
 * sentAt 은 고정 상수다. LocalDateTime.now() 기준 상대 시각을 쓰면
 * 그룹 윈도우 경계(정확히 5분)를 다루는 테스트가 실행 시점에 따라 흔들린다.
 */
fun createNotification(
    id: Long = 1L,
    notifId: String = "notif-1",
    recipientUserId: Long = 1L,
    actorUserId: Long = 2L,
    notificationType: NotificationType = NotificationType.COMMENT,
    targetPhotoId: Long = 10L,
    groupCount: Int = 1,
    title: String = "새 댓글",
    body: String = "상대방님이 댓글을 남겼어요",
    isRead: Boolean = false,
    sentAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
    groupClosedAt: LocalDateTime? = null,
    targetAddress: String? = null,
) = Notification(
    id = id,
    notifId = notifId,
    recipientUserId = recipientUserId,
    actorUserId = actorUserId,
    notificationType = notificationType,
    targetPhotoId = targetPhotoId,
    groupCount = groupCount,
    title = title,
    body = body,
    isRead = isRead,
    sentAt = sentAt,
    groupClosedAt = groupClosedAt,
    targetAddress = targetAddress,
)

/**
 * scheduledAt 은 고정 상수다(계약 2-19). now 기준 상대 시각을 쓰면
 * isDue 경계(now == scheduledAt)를 다루는 테스트가 실행 시점에 따라 흔들린다.
 * recipientUserId != actorUserId 는 도메인 불변식이라 기본값이 서로 달라야 한다.
 */
fun createPendingUploadNotification(
    id: Long = 1L,
    coupleId: Long = 1L,
    recipientUserId: Long = 1L,
    actorUserId: Long = 2L,
    photoIds: List<Long> = listOf(10L),
    scheduledAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 10),
    sentAt: LocalDateTime? = null,
) = PendingUploadNotification(
    id = id,
    coupleId = coupleId,
    recipientUserId = recipientUserId,
    actorUserId = actorUserId,
    photoIds = photoIds,
    scheduledAt = scheduledAt,
    sentAt = sentAt,
)
