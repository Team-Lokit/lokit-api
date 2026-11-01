package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.domain.couple.application.port.CoupleRepositoryPort
import kr.co.lokit.api.domain.photo.domain.PhotoCreatedEvent
import kr.co.lokit.api.fixture.createCouple
import kr.co.lokit.api.fixture.createPendingUploadNotification
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 계약 T12 / 계층(7). 협력자가 2개뿐이라 @InjectMocks 를 쓴다 — 슬라이스4의 수동 조립 요구는
 * UploadNotificationService 가 LockManager 실 인스턴스를 필요로 했던 그 서비스 내부 사정이고,
 * 여기서는 서비스를 통째로 목으로 세우므로 해당되지 않는다.
 *
 * 리스너는 schedule 의 now 를 넘기지 않는다(기본값 LocalDateTime.now()) — any() 로 받는다.
 * 수신자 해석은 전적으로 Couple 도메인(userIds 멤버십 + partnerIdFor)에 위임한다(F10).
 */
@ExtendWith(MockitoExtension::class)
class PhotoUploadNotificationListenerTest {
    @Mock
    lateinit var coupleRepository: CoupleRepositoryPort

    @Mock
    lateinit var uploadNotificationService: UploadNotificationService

    @InjectMocks
    lateinit var listener: PhotoUploadNotificationListener

    @Test
    fun `업로드 이벤트를 받으면 파트너에게 알림을 예약한다`() {
        whenever(coupleRepository.findById(COUPLE_ID))
            .thenReturn(createCouple(id = COUPLE_ID, userIds = listOf(UPLOADER_ID, PARTNER_ID)))
        whenever(uploadNotificationService.schedule(any(), any(), any(), any(), any()))
            .thenReturn(createPendingUploadNotification())

        listener.handlePhotoCreated(uploadEvent())

        verify(uploadNotificationService).schedule(
            coupleId = eq(COUPLE_ID),
            recipientUserId = eq(PARTNER_ID),
            actorUserId = eq(UPLOADER_ID),
            photoId = eq(PHOTO_ID),
            now = any(),
        )
    }

    @Test
    fun `업로더가 커플 멤버가 아니면 예약하지 않는다`() {
        whenever(coupleRepository.findById(COUPLE_ID))
            .thenReturn(createCouple(id = COUPLE_ID, userIds = listOf(PARTNER_ID, OUTSIDER_ID)))

        listener.handlePhotoCreated(uploadEvent())

        verify(uploadNotificationService, never()).schedule(any(), any(), any(), any(), any())
    }

    @Test
    fun `커플이 없으면 예약하지 않는다`() {
        whenever(coupleRepository.findById(COUPLE_ID)).thenReturn(null)

        listener.handlePhotoCreated(uploadEvent())

        verify(uploadNotificationService, never()).schedule(any(), any(), any(), any(), any())
    }

    @Test
    fun `1인 커플(파트너 없음)이면 예약하지 않는다`() {
        whenever(coupleRepository.findById(COUPLE_ID))
            .thenReturn(createCouple(id = COUPLE_ID, userIds = listOf(UPLOADER_ID)))

        listener.handlePhotoCreated(uploadEvent())

        verify(uploadNotificationService, never()).schedule(any(), any(), any(), any(), any())
    }

    @Test
    fun `커플 조회가 실패해도 예외가 전파되지 않는다`() {
        whenever(coupleRepository.findById(COUPLE_ID))
            .thenThrow(IllegalStateException("커플을 찾을 수 없습니다."))

        assertDoesNotThrow<Unit> { listener.handlePhotoCreated(uploadEvent()) }

        verify(uploadNotificationService, never()).schedule(any(), any(), any(), any(), any())
    }

    private fun uploadEvent() =
        PhotoCreatedEvent(
            albumId = ALBUM_ID,
            coupleId = COUPLE_ID,
            longitude = LONGITUDE,
            latitude = LATITUDE,
            photoId = PHOTO_ID,
            uploaderUserId = UPLOADER_ID,
        )

    companion object {
        private const val ALBUM_ID = 3L
        private const val COUPLE_ID = 1L
        private const val PHOTO_ID = 10L
        private const val UPLOADER_ID = 2L
        private const val PARTNER_ID = 5L
        private const val OUTSIDER_ID = 9L
        private const val LONGITUDE = 127.0
        private const val LATITUDE = 37.5
    }
}
