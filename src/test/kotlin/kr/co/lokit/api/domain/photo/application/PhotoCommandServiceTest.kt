package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.domain.album.application.CurrentCoupleAlbumResolver
import kr.co.lokit.api.domain.map.application.MapPhotosCacheService
import kr.co.lokit.api.domain.map.application.port.RegionResolverPort
import kr.co.lokit.api.domain.map.application.port.`in`.SearchLocationUseCase
import kr.co.lokit.api.domain.map.domain.LocationInfoReadModel
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.photo.application.port.PhotoStoragePort
import kr.co.lokit.api.domain.photo.domain.PhotoCreatedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoDeletedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoLocationUpdatedEvent
import kr.co.lokit.api.domain.photo.domain.PresignedUpload
import kr.co.lokit.api.fixture.createLocation
import kr.co.lokit.api.fixture.createPhoto
import kr.co.lokit.api.fixture.createUpdatePhotoRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class PhotoCommandServiceTest {
    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @Mock
    lateinit var currentCoupleAlbumResolver: CurrentCoupleAlbumResolver

    @Mock
    lateinit var photoStoragePort: PhotoStoragePort

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var mapQueryService: SearchLocationUseCase

    @Mock
    lateinit var mapPhotosCacheService: MapPhotosCacheService

    @Mock
    lateinit var cacheManager: CacheManager

    @Mock
    lateinit var regionResolverPort: RegionResolverPort

    @InjectMocks
    lateinit var photoCommandService: PhotoCommandService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T = any<T>() as T

    @BeforeEach
    fun setUpRegionResolver() {
        // 기본: 모든 좌표가 강남구 역삼1동에 속한다. 거부 시나리오는 테스트에서 특정 좌표를 null 로 재스텁한다.
        lenient().`when`(regionResolverPort.resolveEupMyeonDongCode(anyDouble(), anyDouble())).thenReturn("11680101")
    }

    @Test
    fun `사진을 생성할 수 있다`() {
        val photo = createPhoto(albumId = 1L, location = createLocation(127.0, 37.5))
        val savedPhoto =
            createPhoto(
                id = 1L,
                albumId = 1L,
                coupleId = 1L,
                url = "https://example.com/picture.png",
                location = createLocation(127.0, 37.5),
            )
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)

        val result = photoCommandService.create(photo)

        assertEquals(1L, result.id)
        verify(eventPublisher).publishEvent(any(PhotoCreatedEvent::class.java))
    }

    @Test
    fun `사진을 생성하면 발행된 이벤트의 photoId가 저장된 사진의 id다`() {
        // 입력 사진의 id는 0L, 저장된 사진의 id는 42L 로 명확히 구분한다(F3).
        // 저장 전 객체(effectivePhoto)를 실으면 photoId 가 0L 이 되어 알림이 영원히 안 나간다.
        val photo = createPhoto(albumId = 1L, location = createLocation(127.0, 37.5))
        val savedPhoto =
            createPhoto(
                id = 42L,
                albumId = 1L,
                coupleId = 1L,
                location = createLocation(127.0, 37.5),
            )
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)

        photoCommandService.create(photo)

        val captor = argumentCaptor<PhotoCreatedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(42L, captor.firstValue.photoId)
    }

    @Test
    fun `사진을 생성하면 발행된 이벤트의 uploaderUserId가 업로더 id다`() {
        // 입력 사진의 uploadedById(1L)와 저장된 사진의 uploadedById(7L)를 다르게 둔다.
        // 같은 값이면 이벤트가 saved 에서 왔는지 effectivePhoto 에서 왔는지 구분할 수 없다(B2).
        val photo = createPhoto(albumId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        val savedPhoto =
            createPhoto(
                id = 42L,
                albumId = 1L,
                coupleId = 1L,
                uploadedById = 7L,
                location = createLocation(127.0, 37.5),
            )
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)

        photoCommandService.create(photo)

        val captor = argumentCaptor<PhotoCreatedEvent>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(7L, captor.firstValue.uploaderUserId)
    }

    @Test
    fun `위치가 없는 (0,0) 사진도 행정구역 판정에서 거부된다`() {
        // 상충 지점 #3 (A) 엄격 거부: (0,0) 은 위치 없음 센티넬로 판정을 건너뛰지 않는다.
        // R33 과 달리 hasLocation()==false 를 이유로 판정을 생략하는 구현(Q1-B)을 막는다.
        val photo = createPhoto(albumId = 1L, location = createLocation(0.0, 0.0))
        `when`(regionResolverPort.resolveEupMyeonDongCode(0.0, 0.0)).thenReturn(null)

        assertThrows<BusinessException.PhotoLocationOutOfRegionException> {
            photoCommandService.create(photo)
        }
        verify(photoRepository, never()).save(anyObject())
        verifyNoInteractions(photoStoragePort, eventPublisher, mapPhotosCacheService, cacheManager)
    }

    @Test
    fun `사진을 삭제할 수 있다`() {
        val photo = createPhoto(id = 1L, url = "https://example.com/photo.jpg", uploadedById = 1L)
        `when`(photoRepository.findById(1L)).thenReturn(photo)

        photoCommandService.delete(1L, 1L)

        verify(photoRepository).deleteById(1L)
        verify(eventPublisher).publishEvent(PhotoDeletedEvent(photoUrl = photo.url))
    }

    @Test
    fun `사진을 수정할 수 있다`() {
        val request = createUpdatePhotoRequest(1L, 127.1, 37.6, description = "수정된 설명")
        val originalPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        val updatedPhoto =
            createPhoto(
                id = 1L,
                albumId = 1L,
                coupleId = 1L,
                description = "수정된 설명",
                location = createLocation(127.1, 37.6),
                uploadedById = 1L,
            )
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(photoRepository.update(anyObject())).thenReturn(updatedPhoto)

        val result = photoCommandService.update(1L, 1L, request.description, request.longitude, request.latitude, 1L)

        assertEquals("수정된 설명", result.description)
    }

    @Test
    fun `사진 위치 수정 시 이벤트가 발행된다`() {
        val request = createUpdatePhotoRequest(1L, longitude = 128.0, latitude = 38.0)
        val originalPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        val updatedPhoto =
            createPhoto(
                id = 1L,
                albumId = 1L,
                coupleId = 1L,
                location = createLocation(longitude = 128.0, latitude = 38.0),
                uploadedById = 1L,
            )
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(photoRepository.update(anyObject())).thenReturn(updatedPhoto)

        photoCommandService.update(1L, 1L, request.description, request.longitude, request.latitude, 1L)

        verify(eventPublisher).publishEvent(anyObject<PhotoLocationUpdatedEvent>())
    }

    @Test
    fun `사진 수정 시 albumId가 null이면 기본 앨범으로 이동한다`() {
        val defaultAlbum = kr.co.lokit.api.fixture.createAlbum(id = 9L, title = "전체보기", isDefault = true)
        val originalPhoto =
            createPhoto(id = 1L, albumId = 3L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        val updatedPhoto =
            createPhoto(id = 1L, albumId = 9L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        `when`(currentCoupleAlbumResolver.requireDefaultAlbum(1L, ErrorField.UPLOADED_BY_ID)).thenReturn(defaultAlbum)
        `when`(photoRepository.update(anyObject())).thenReturn(updatedPhoto)

        val result = photoCommandService.update(1L, null, "설명", null, null, 1L)

        verify(currentCoupleAlbumResolver).requireDefaultAlbum(1L, ErrorField.UPLOADED_BY_ID)
        assertEquals(9L, result.albumId)
    }

    @Test
    fun `사진 수정에서 기본 앨범이 없으면 예외가 발생한다`() {
        val originalPhoto =
            createPhoto(id = 1L, albumId = 3L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        `when`(currentCoupleAlbumResolver.requireDefaultAlbum(1L, ErrorField.UPLOADED_BY_ID))
            .thenThrow(BusinessException.DefaultAlbumNotFoundForUserException())

        assertThrows<BusinessException.DefaultAlbumNotFoundForUserException> {
            photoCommandService.update(1L, null, "설명", null, null, 1L)
        }
    }

    @Test
    fun `presigned URL을 생성할 수 있다`() {
        val expected =
            PresignedUpload(
                presignedUrl = "https://bucket.s3.amazonaws.com/presigned",
                objectUrl = "https://bucket.s3.amazonaws.com/photos/test-key/image.jpg",
            )
        `when`(photoStoragePort.generatePresignedUrl(anyString(), anyString())).thenReturn(expected)

        val result = photoCommandService.generatePresignedUrl("test-key", "image/jpeg")

        assertEquals(expected.presignedUrl, result.presignedUrl)
        assertEquals(expected.objectUrl, result.objectUrl)
    }

    @Test
    fun `idempotencyKey가 null이면 UUID가 생성된다`() {
        val expected =
            PresignedUpload(
                presignedUrl = "https://bucket.s3.amazonaws.com/presigned",
                objectUrl = "https://bucket.s3.amazonaws.com/photos/uuid/image.jpg",
            )
        `when`(photoStoragePort.generatePresignedUrl(anyString(), anyString())).thenReturn(expected)

        val result = photoCommandService.generatePresignedUrl(null, "image/jpeg")

        assertEquals(expected.presignedUrl, result.presignedUrl)
        verify(photoStoragePort).generatePresignedUrl(anyString(), anyString())
    }

    @Test
    fun `albumId가 없으면 기본 앨범으로 사진이 생성된다`() {
        val photo = createPhoto(albumId = 0L, location = createLocation(127.0, 37.5))
        val defaultAlbum = kr.co.lokit.api.fixture.createAlbum(id = 5L, title = "default", isDefault = true)
        val savedPhoto = createPhoto(id = 1L, albumId = 5L, coupleId = 1L, location = createLocation(127.0, 37.5))
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        `when`(currentCoupleAlbumResolver.requireDefaultAlbum(1L, ErrorField.UPLOADED_BY_ID)).thenReturn(defaultAlbum)
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)

        val result = photoCommandService.create(photo)

        assertEquals(5L, result.albumId)
    }

    @Test
    fun `기본 앨범이 없으면 DefaultAlbumNotFoundForUserException이 발생한다`() {
        val photo = createPhoto(albumId = 0L, location = createLocation(127.0, 37.5))
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        `when`(currentCoupleAlbumResolver.requireDefaultAlbum(1L, ErrorField.UPLOADED_BY_ID))
            .thenThrow(BusinessException.DefaultAlbumNotFoundForUserException())

        val exception =
            assertThrows<ExecutionException> {
                photoCommandService.create(photo)
            }
        assertInstanceOf(BusinessException.DefaultAlbumNotFoundForUserException::class.java, exception.cause)
    }

    @Test
    fun `사진 생성 시 위치 기반 캐시가 제거된다`() {
        val photo = createPhoto(albumId = 1L, location = createLocation(127.0, 37.5))
        val savedPhoto = createPhoto(id = 1L, albumId = 1L, coupleId = 1L, location = createLocation(127.0, 37.5))
//        val mockCache = mock(Cache::class.java)
        doNothing().`when`(photoStoragePort).verifyFileExists(photo.url)
        doNothing().`when`(currentCoupleAlbumResolver).validateAlbumBelongsToCurrentCouple(1L, 1L, ErrorField.UPLOADED_BY_ID)
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 강남구", placeName = null, regionName = "강남구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)
//        `when`(cacheManager.getCache("coupleAlbums")).thenReturn(mockCache)

        photoCommandService.create(photo)

        verify(mapPhotosCacheService).evictForPhotoMutation(1L, 1L, 127.0, 37.5)
//        verify(mockCache).evict(1L)
    }

    @Test
    fun `사진 삭제 시 위치 기반 캐시가 제거된다`() {
        val photo =
            createPhoto(
                id = 1L,
                url = "https://example.com/photo.jpg",
                uploadedById = 1L,
                coupleId = 1L,
                albumId = 2L,
                location = createLocation(127.1, 37.6),
            )
//        val mockCache = mock(Cache::class.java)
        `when`(photoRepository.findById(1L)).thenReturn(photo)
//        `when`(cacheManager.getCache("coupleAlbums")).thenReturn(mockCache)

        photoCommandService.delete(1L, 1L)

        verify(mapPhotosCacheService).evictForPhotoMutation(1L, 2L, 127.1, 37.6)
//        verify(mockCache).evict(1L)
    }

    @Test
    fun `행정구역에 속하지 않는 좌표로 사진을 생성하면 예외가 발생한다`() {
        val photo = createPhoto(albumId = 1L, location = createLocation(130.0, 30.0))
        `when`(regionResolverPort.resolveEupMyeonDongCode(130.0, 30.0)).thenReturn(null)

        // fork 밖에서 던져야 하므로 ExecutionException 으로 감싸지지 않은 예외 그대로여야 한다(§0-4).
        assertThrows<BusinessException.PhotoLocationOutOfRegionException> {
            photoCommandService.create(photo)
        }
        verify(photoRepository, never()).save(anyObject())
        verifyNoInteractions(photoStoragePort, eventPublisher, mapPhotosCacheService, cacheManager)
    }

    @Test
    fun `사진을 생성하면 좌표의 읍면동 코드가 저장된다`() {
        val photo = createPhoto(albumId = 1L, location = createLocation(126.98, 37.57))
        val savedPhoto = createPhoto(id = 1L, albumId = 1L, coupleId = 1L, location = createLocation(126.98, 37.57))
        `when`(regionResolverPort.resolveEupMyeonDongCode(126.98, 37.57)).thenReturn("11110101")
        `when`(mapQueryService.getLocationInfo(anyDouble(), anyDouble())).thenReturn(
            LocationInfoReadModel(address = "서울 종로구", placeName = null, regionName = "종로구"),
        )
        `when`(photoRepository.save(anyObject())).thenReturn(savedPhoto)

        photoCommandService.create(photo)

        val captor = argumentCaptor<kr.co.lokit.api.domain.photo.domain.Photo>()
        verify(photoRepository).save(captor.capture())
        assertEquals("11110101", captor.firstValue.regionCode)
    }

    @Test
    fun `행정구역에 속하지 않는 좌표로 위치를 수정하면 예외가 발생하고 저장하지 않는다`() {
        val originalPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
                .withRegionCode("11680101")
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        `when`(regionResolverPort.resolveEupMyeonDongCode(130.0, 30.0)).thenReturn(null)

        assertThrows<BusinessException.PhotoLocationOutOfRegionException> {
            photoCommandService.update(1L, 1L, null, 130.0, 30.0, 1L)
        }
        verify(photoRepository, never()).update(anyObject())
        verifyNoInteractions(eventPublisher, mapPhotosCacheService, cacheManager)
    }

    @Test
    fun `위치를 수정하면 병합된 좌표로 지역 코드를 재배정한다`() {
        val originalPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
                .withRegionCode("11680101")
        val updatedPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(128.0, 37.5))
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        `when`(regionResolverPort.resolveEupMyeonDongCode(128.0, 37.5)).thenReturn("42150101")
        `when`(photoRepository.update(anyObject())).thenReturn(updatedPhoto)

        photoCommandService.update(1L, 1L, null, 128.0, null, 1L)

        verify(regionResolverPort).resolveEupMyeonDongCode(128.0, 37.5)
        val captor = argumentCaptor<kr.co.lokit.api.domain.photo.domain.Photo>()
        verify(photoRepository).update(captor.capture())
        assertEquals("42150101", captor.firstValue.regionCode)
    }

    @Test
    fun `위치를 바꾸지 않는 수정은 지역을 다시 판정하지 않는다`() {
        val originalPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, location = createLocation(127.0, 37.5))
                .withRegionCode("11680101")
        val updatedPhoto =
            createPhoto(id = 1L, albumId = 1L, coupleId = 1L, uploadedById = 1L, description = "설명만 수정")
        `when`(photoRepository.findById(1L)).thenReturn(originalPhoto)
        `when`(photoRepository.update(anyObject())).thenReturn(updatedPhoto)

        photoCommandService.update(1L, 1L, "설명만 수정", null, null, 1L)

        verify(regionResolverPort, never()).resolveEupMyeonDongCode(anyDouble(), anyDouble())
        val captor = argumentCaptor<kr.co.lokit.api.domain.photo.domain.Photo>()
        verify(photoRepository).update(captor.capture())
        assertEquals("11680101", captor.firstValue.regionCode)
    }
}
