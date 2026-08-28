package kr.co.lokit.api.domain.map.application

import kr.co.lokit.api.domain.photo.domain.PhotoCreatedEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * F1/B3 못박기: `PhotoCreatedEvent` 에 photoId/uploaderUserId 가 붙어도
 * 이 소비자는 기존 4개 필드만 읽고 그대로 위임해야 한다.
 * 이벤트가 또 확장되었을 때 이 테스트가 깨지면 지도 경계 갱신에 회귀가 생긴 것이다.
 */
@ExtendWith(MockitoExtension::class)
class AlbumBoundsEventHandlerTest {
    @Mock
    lateinit var albumBoundsService: AlbumBoundsService

    @InjectMocks
    lateinit var albumBoundsEventHandler: AlbumBoundsEventHandler

    @Test
    fun `PhotoCreatedEvent를 받으면 albumId와 coupleId와 좌표로 경계를 갱신한다`() {
        val event =
            PhotoCreatedEvent(
                albumId = 11L,
                coupleId = 22L,
                longitude = 127.0,
                latitude = 37.5,
                photoId = 99L,
                uploaderUserId = 7L,
            )

        albumBoundsEventHandler.handlePhotoCreated(event)

        verify(albumBoundsService).updateBoundsOnPhotoAdd(
            albumId = 11L,
            coupleId = 22L,
            longitude = 127.0,
            latitude = 37.5,
        )
        // photoId/uploaderUserId 로 인한 추가 협력(사진 재조회 등)이 생기면 여기서 잡힌다.
        verifyNoMoreInteractions(albumBoundsService)
    }
}
