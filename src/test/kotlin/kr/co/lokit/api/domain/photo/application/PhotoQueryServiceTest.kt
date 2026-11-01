package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.constants.CoupleStatus
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.album.application.port.AlbumRepositoryPort
import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.map.application.port.MapClientPort
import kr.co.lokit.api.domain.map.domain.LocationInfoReadModel
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.domain.DeIdentifiedUserProfile
import kr.co.lokit.api.domain.photo.domain.PhotoViewedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoViewerRole
import kr.co.lokit.api.fixture.createCouple
import kr.co.lokit.api.fixture.createPhotoDetail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class PhotoQueryServiceTest {

    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @Mock
    lateinit var albumRepository: AlbumRepositoryPort

    @Mock
    lateinit var mapClientPort: MapClientPort

    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    lateinit var photoQueryService: PhotoQueryService

    @Test
    fun `사진 상세 정보를 조회할 수 있다`() {
        val photoDetail = createPhotoDetail(description = "테스트 사진", uploadedById = 2L)
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(
            createCouple(id = 1L, userIds = listOf(1L, 2L), status = CoupleStatus.CONNECTED),
        )

        val result = photoQueryService.getPhotoDetail(1L, 1L)

        assertEquals(1L, result.id)
        assertEquals("여행", result.albumName)
        assertEquals("서울 강남구", result.address)
        assertEquals("테스트", result.uploaderName)
    }

    @Test
    fun `존재하지 않는 사진 조회 시 예외가 발생한다`() {
        `when`(photoRepository.findDetailById(999L)).thenThrow(
            BusinessException.ResourceNotFoundException(
                "Photo(id=999)을(를) 찾을 수 없습니다",
            ),
        )

        assertThrows<BusinessException.ResourceNotFoundException> {
            photoQueryService.getPhotoDetail(999L, 1L)
        }
    }

    @Test
    fun `커플 연결 해제 시 끊은 사용자의 프로필이 비식별 처리된다`() {
        val disconnectedByUserId = 2L
        val viewerUserId = 1L
        val photoDetail = createPhotoDetail(
            uploadedById = disconnectedByUserId,
            uploaderName = "탈퇴한유저",
            uploaderProfileImageUrl = "https://example.com/profile.jpg",
        )
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(
                id = 1L,
                userIds = listOf(viewerUserId, disconnectedByUserId),
                status = CoupleStatus.DISCONNECTED,
                disconnectedByUserId = disconnectedByUserId,
            ),
        )

        val result = photoQueryService.getPhotoDetail(1L, viewerUserId)

        assertEquals(DeIdentifiedUserProfile.DISPLAY_NAME, result.uploaderName)
        assertNull(result.uploaderProfileImageUrl)
    }

    @Test
    fun `커플 연결 해제 시 본인의 프로필은 비식별 처리되지 않는다`() {
        val disconnectedByUserId = 2L
        val viewerUserId = 1L
        val photoDetail = createPhotoDetail(
            uploadedById = viewerUserId,
            uploaderName = "나",
            uploaderProfileImageUrl = "https://example.com/my-profile.jpg",
        )
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(
            createCouple(
                id = 1L,
                userIds = listOf(viewerUserId, disconnectedByUserId),
                status = CoupleStatus.DISCONNECTED,
                disconnectedByUserId = disconnectedByUserId,
            ),
        )

        val result = photoQueryService.getPhotoDetail(1L, viewerUserId)

        assertEquals("나", result.uploaderName)
        assertEquals("https://example.com/my-profile.jpg", result.uploaderProfileImageUrl)
    }

    @Test
    fun `사진 상세를 조회하면 조회 이벤트가 발행된다`() {
        val photoDetail = createPhotoDetail(uploadedById = 2L)
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(
            createCouple(id = 1L, userIds = listOf(1L, 2L), status = CoupleStatus.CONNECTED),
        )

        photoQueryService.getPhotoDetail(1L, 1L)

        val captor = argumentCaptor<PhotoViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        val event = captor.firstValue
        assertEquals(1L, event.photoId)
        assertEquals(1L, event.viewerUserId)
        assertEquals(2L, event.photoOwnerId)
    }

    @Test
    fun `파트너가 조회하면 PARTNER 역할로 발행된다`() {
        val photoDetail = createPhotoDetail(uploadedById = 2L)
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(
            createCouple(id = 1L, userIds = listOf(1L, 2L), status = CoupleStatus.CONNECTED),
        )

        photoQueryService.getPhotoDetail(1L, 1L)

        val captor = argumentCaptor<PhotoViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(PhotoViewerRole.PARTNER, captor.firstValue.viewerRole)
    }

    @Test
    fun `사진이 없으면 조회 이벤트가 발행되지 않는다`() {
        `when`(photoRepository.findDetailById(999L)).thenThrow(
            BusinessException.ResourceNotFoundException(
                "Photo(id=999)을(를) 찾을 수 없습니다",
            ),
        )

        assertThrows<BusinessException.ResourceNotFoundException> {
            photoQueryService.getPhotoDetail(999L, 1L)
        }

        verify(eventPublisher, never()).publishEvent(org.mockito.kotlin.any())
    }

    @Test
    fun `사진 상세 조회는 커플을 한 번만 조회한다`() {
        val photoDetail = createPhotoDetail(uploadedById = 2L)
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(1L)).thenReturn(
            createCouple(id = 1L, userIds = listOf(1L, 2L), status = CoupleStatus.CONNECTED),
        )

        photoQueryService.getPhotoDetail(1L, 1L)

        verify(coupleRepository, times(1)).findByUserId(1L)
    }

    /**
     * 커버리지 리뷰(슬라이스8)로 못박은 경로: 커플이 없는(솔로) 사용자가 자기 사진을 볼 때도
     * OWNER로 이벤트가 발행되고 비식별 처리가 되지 않아야 한다. `PhotoViewerRole.of()`의
     * couple=null 분기는 단위 테스트로만 검증돼 있었고 서비스 레벨 통합 경로는 비어 있었다.
     */
    @Test
    fun `커플이 없는 솔로 사용자가 자기 사진을 보면 OWNER로 발행되고 비식별되지 않는다`() {
        val viewerUserId = 1L
        val photoDetail = createPhotoDetail(uploadedById = viewerUserId, uploaderName = "나")
        `when`(photoRepository.findDetailById(1L)).thenReturn(photoDetail)
        `when`(mapClientPort.reverseGeocode(127.0, 37.5)).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(coupleRepository.findByUserId(viewerUserId)).thenReturn(null)

        val result = photoQueryService.getPhotoDetail(1L, viewerUserId)

        assertEquals("나", result.uploaderName)
        val captor = argumentCaptor<PhotoViewedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(PhotoViewerRole.OWNER, captor.firstValue.viewerRole)
    }
}
