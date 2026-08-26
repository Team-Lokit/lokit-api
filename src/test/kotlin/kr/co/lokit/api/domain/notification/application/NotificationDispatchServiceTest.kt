package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import kr.co.lokit.api.common.concurrency.LockManager
import kr.co.lokit.api.domain.notification.application.port.DeviceTokenRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.PushSenderPort
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.notification.domain.PushMessage
import kr.co.lokit.api.domain.notification.domain.PushSendResult
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.fixture.createDeviceToken
import kr.co.lokit.api.fixture.createNotification
import kr.co.lokit.api.fixture.createUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @InjectMocks 를 쓰지 않는다(계약 T7). LockManager 는 목이 아니라 실 인스턴스여야
 * withLock 의 람다가 실제로 실행된다 — 목으로 두면 람다가 돌지 않아 검증이 무의미해진다.
 */
@ExtendWith(MockitoExtension::class)
class NotificationDispatchServiceTest {
    @Mock
    lateinit var notificationRepository: NotificationRepositoryPort

    @Mock
    lateinit var deviceTokenRepository: DeviceTokenRepositoryPort

    @Mock
    lateinit var userRepository: UserRepositoryPort

    @Mock
    lateinit var appEventLogPort: AppEventLogPort

    @Mock
    lateinit var pushSenderPort: PushSenderPort

    lateinit var service: NotificationDispatchService

    @BeforeEach
    fun setUp() {
        service = newService(pushSenderPort)
    }

    private fun newService(sender: PushSenderPort?): NotificationDispatchService =
        NotificationDispatchService(
            notificationRepository,
            deviceTokenRepository,
            userRepository,
            appEventLogPort,
            LockManager(),
            sender,
        )

    @Test
    fun `열린 윈도우가 없으면 알림을 새로 저장한다`() {
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(null)
        whenever(userRepository.findById(ACTOR_ID)).thenReturn(createUser(id = ACTOR_ID, name = "지민"))
        whenever(notificationRepository.save(any())).thenReturn(createNotification())

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        val captor = argumentCaptor<Notification>()
        verify(notificationRepository).save(captor.capture())
        val saved = captor.firstValue
        assertTrue(saved.notifId.isNotBlank())
        assertEquals(RECIPIENT_ID, saved.recipientUserId)
        assertEquals(ACTOR_ID, saved.actorUserId)
        assertEquals(PHOTO_ID, saved.targetPhotoId)
        assertEquals(1, saved.groupCount)
        assertEquals("새 댓글", saved.title)
        assertEquals("지민님이 댓글을 남겼어요", saved.body)
        assertEquals(NOW, saved.sentAt)
    }

    @Test
    fun `열린 윈도우가 없으면 즉시 푸시 1건을 보낸다`() {
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(null)
        whenever(notificationRepository.save(any())).thenReturn(
            createNotification(notifId = "notif-9", title = "새 댓글", body = "지민님이 댓글을 남겼어요"),
        )
        whenever(deviceTokenRepository.findAllByUserId(RECIPIENT_ID)).thenReturn(
            listOf(
                createDeviceToken(id = 1L, userId = RECIPIENT_ID, token = "fcm-a"),
                createDeviceToken(id = 2L, userId = RECIPIENT_ID, token = "fcm-b"),
            ),
        )
        whenever(pushSenderPort.send(any())).thenReturn(PushSendResult(successTokens = listOf("fcm-a", "fcm-b")))

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        val captor = argumentCaptor<PushMessage>()
        verify(pushSenderPort).send(captor.capture())
        val message = captor.firstValue
        assertEquals(listOf("fcm-a", "fcm-b"), message.tokens)
        assertEquals("새 댓글", message.title)
        assertEquals("지민님이 댓글을 남겼어요", message.body)
        assertEquals("notif-9", message.data["notifId"])
        assertEquals("COMMENT", message.data["notifType"])
        assertEquals("10", message.data["photoId"])
        assertEquals("1", message.data["groupCount"])
    }

    @Test
    fun `열린 윈도우가 있으면 그룹 개수만 올리고 푸시를 보내지 않는다`() {
        val openWindow = createNotification(id = 42L, sentAt = NOW, groupClosedAt = null)
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(openWindow)

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW.plusMinutes(2),
        )

        verify(notificationRepository).increaseGroupCount(42L)
        verify(notificationRepository, never()).save(any())
        verify(pushSenderPort, never()).send(any())
    }

    @Test
    fun `창이 만료된 알림만 남아 있으면 새 윈도우를 연다`() {
        val expiredWindow = createNotification(id = 42L, sentAt = NOW.minusMinutes(6), groupClosedAt = null)
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(expiredWindow)
        whenever(notificationRepository.save(any())).thenReturn(createNotification())

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        verify(notificationRepository).save(any())
        verify(notificationRepository, never()).increaseGroupCount(any())
    }

    @Test
    fun `푸시를 보내면 push_send 이벤트에 알림 식별자와 종류와 그룹 개수가 기록된다`() {
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(null)
        whenever(notificationRepository.save(any())).thenReturn(createNotification(notifId = "notif-9"))
        whenever(deviceTokenRepository.findAllByUserId(RECIPIENT_ID)).thenReturn(
            listOf(createDeviceToken(id = 1L, userId = RECIPIENT_ID, token = "fcm-a")),
        )
        whenever(pushSenderPort.send(any())).thenReturn(
            PushSendResult(
                successTokens = listOf("fcm-a"),
                failedTokens = listOf("fcm-b"),
                invalidTokens = listOf("fcm-c"),
            ),
        )

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        val captor = argumentCaptor<Map<String, Any?>>()
        verify(appEventLogPort).record(
            eventName = eq("push_send"),
            userId = eq(RECIPIENT_ID),
            notifId = eq("notif-9"),
            notifType = eq("COMMENT"),
            params = captor.capture(),
        )
        val params = captor.firstValue
        assertEquals(1, params["group_count"])
        assertEquals(1, params["success_count"])
        assertEquals(2, params["failure_count"])
        assertEquals(1, params["invalid_count"])
    }

    @Test
    fun `푸시 발송기가 없으면 알림은 저장되지만 발송과 로깅은 생략된다`() {
        val serviceWithoutSender = newService(null)
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(null)
        whenever(notificationRepository.save(any())).thenReturn(createNotification())

        serviceWithoutSender.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        verify(notificationRepository).save(any())
        verify(deviceTokenRepository, never()).findAllByUserId(any())
        verify(appEventLogPort, never()).record(any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `등록된 디바이스 토큰이 없으면 발송하지 않는다`() {
        whenever(notificationRepository.findLatestUnclosedByRecipientAndPhoto(RECIPIENT_ID, PHOTO_ID))
            .thenReturn(null)
        whenever(notificationRepository.save(any())).thenReturn(createNotification())
        whenever(deviceTokenRepository.findAllByUserId(RECIPIENT_ID)).thenReturn(emptyList())

        service.notifyPhotoInteraction(
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            notificationType = NotificationType.COMMENT,
            now = NOW,
        )

        verify(pushSenderPort, never()).send(any())
    }

    @Test
    fun `마감 시 그룹 개수가 2 이상이면 통합 문구로 본문을 갱신하고 후속 1건을 발송한다`() {
        val window = createNotification(id = 42L, actorUserId = ACTOR_ID, groupCount = 3)
        whenever(userRepository.findById(ACTOR_ID)).thenReturn(createUser(id = ACTOR_ID, name = "지민"))
        whenever(notificationRepository.closeGroupWindow(eq(42L), eq(NOW), any())).thenReturn(
            createNotification(id = 42L, groupCount = 3, body = "지민님이 댓글 3개를 남겼어요", groupClosedAt = NOW),
        )
        whenever(deviceTokenRepository.findAllByUserId(RECIPIENT_ID)).thenReturn(
            listOf(createDeviceToken(id = 1L, userId = RECIPIENT_ID, token = "fcm-a")),
        )
        whenever(pushSenderPort.send(any())).thenReturn(PushSendResult(successTokens = listOf("fcm-a")))

        service.closeGroupWindow(window, NOW)

        val captor = argumentCaptor<String>()
        verify(notificationRepository).closeGroupWindow(eq(42L), eq(NOW), captor.capture())
        assertEquals("지민님이 댓글 3개를 남겼어요", captor.firstValue)
        verify(pushSenderPort).send(any())
    }

    @Test
    fun `마감 시 그룹 개수가 1이면 마감 표시만 하고 발송하지 않는다`() {
        val window = createNotification(id = 42L, groupCount = 1, body = "상대방님이 댓글을 남겼어요")
        whenever(notificationRepository.closeGroupWindow(42L, NOW, "상대방님이 댓글을 남겼어요")).thenReturn(
            createNotification(id = 42L, groupCount = 1, groupClosedAt = NOW),
        )

        service.closeGroupWindow(window, NOW)

        verify(notificationRepository).closeGroupWindow(42L, NOW, "상대방님이 댓글을 남겼어요")
        verify(pushSenderPort, never()).send(any())
    }

    @Test
    fun `dispatchImmediately는 알림을 저장하고 즉시 푸시를 보낸다`() {
        val notification = uploadNotification()
        // 푸시가 '저장 후' 값으로 만들어지는지 못박기 위해 notifId 를 일부러 다르게 돌려준다.
        whenever(notificationRepository.save(any()))
            .thenReturn(notification.copy(id = SAVED_ID, notifId = "upload-saved"))
        whenever(deviceTokenRepository.findAllByUserId(RECIPIENT_ID)).thenReturn(
            listOf(createDeviceToken(id = 1L, userId = RECIPIENT_ID, token = "fcm-a")),
        )
        whenever(pushSenderPort.send(any())).thenReturn(PushSendResult(successTokens = listOf("fcm-a")))

        val result = service.dispatchImmediately(notification)

        val savedCaptor = argumentCaptor<Notification>()
        verify(notificationRepository).save(savedCaptor.capture())
        val saved = savedCaptor.firstValue
        assertEquals(NotificationType.UPLOAD, saved.notificationType)
        assertEquals("upload-1", saved.notifId)
        assertEquals(RECIPIENT_ID, saved.recipientUserId)
        assertEquals(2, saved.groupCount)
        assertEquals(UPLOAD_ADDRESS, saved.targetAddress)
        // 태어날 때 이미 마감돼 있어야 슬라이스3 마감 배치가 이 알림을 덮어쓰지 않는다(D5/F13).
        assertEquals(NOW, saved.groupClosedAt)
        assertEquals(SAVED_ID, result.id)

        val pushCaptor = argumentCaptor<PushMessage>()
        verify(pushSenderPort).send(pushCaptor.capture())
        val message = pushCaptor.firstValue
        assertEquals(listOf("fcm-a"), message.tokens)
        assertEquals("새 사진", message.title)
        assertEquals("upload-saved", message.data["notifId"])
        assertEquals("UPLOAD", message.data["notifType"])
        assertEquals(UPLOAD_ADDRESS, message.data["targetAddress"])

        val paramsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(appEventLogPort).record(
            eventName = eq("push_send"),
            userId = eq(RECIPIENT_ID),
            notifId = eq("upload-saved"),
            notifType = eq("UPLOAD"),
            params = paramsCaptor.capture(),
        )
        assertEquals(2, paramsCaptor.firstValue["group_count"])
    }

    @Test
    fun `pushSenderPort가 없으면 dispatchImmediately는 저장만 하고 발송을 생략한다`() {
        val serviceWithoutSender = newService(null)
        val notification = uploadNotification()
        whenever(notificationRepository.save(any())).thenReturn(notification.copy(id = SAVED_ID))

        val result = serviceWithoutSender.dispatchImmediately(notification)

        assertEquals(SAVED_ID, result.id)
        verify(notificationRepository).save(any())
        verify(deviceTokenRepository, never()).findAllByUserId(any())
        verify(appEventLogPort, never()).record(any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
    }

    /** N-2 는 완성된 알림을 넘긴다 — 서비스는 문구를 만들지 않는다(D7). */
    private fun uploadNotification(): Notification =
        Notification.upload(
            notifId = "upload-1",
            recipientUserId = RECIPIENT_ID,
            actorUserId = ACTOR_ID,
            targetPhotoId = PHOTO_ID,
            targetAddress = UPLOAD_ADDRESS,
            photoCount = 2,
            title = "새 사진",
            body = "지민님이 ${UPLOAD_ADDRESS}에 사진 2장을 올렸어요",
            sentAt = NOW,
        )

    companion object {
        private const val RECIPIENT_ID = 1L
        private const val ACTOR_ID = 2L
        private const val PHOTO_ID = 10L
        private const val SAVED_ID = 77L
        private const val UPLOAD_ADDRESS = "서울 마포구 연남동"
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
    }
}
