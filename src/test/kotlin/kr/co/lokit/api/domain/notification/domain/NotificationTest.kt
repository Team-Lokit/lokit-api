package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationTest {

    /**
     * 시간 픽스처는 고정 상수다. LocalDateTime.now() 기준 상대 시각을 쓰면
     * 실행 시점(자정 경계·서머타임)에 따라 결과가 흔들린다.
     */
    private val sentAt = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun notification(
        notifId: String = "notif-1",
        recipientUserId: Long = 1L,
        actorUserId: Long = 2L,
        notificationType: NotificationType = NotificationType.COMMENT,
        targetPhotoId: Long = 10L,
        groupCount: Int = 1,
        title: String = "새 댓글",
        body: String = "상대방님이 댓글을 남겼어요",
        groupClosedAt: LocalDateTime? = null,
    ) = Notification(
        notifId = notifId,
        recipientUserId = recipientUserId,
        actorUserId = actorUserId,
        notificationType = notificationType,
        targetPhotoId = targetPhotoId,
        groupCount = groupCount,
        title = title,
        body = body,
        sentAt = sentAt,
        groupClosedAt = groupClosedAt,
    )

    @Test
    fun `알림은 수신자와 행위자와 대상 사진과 종류를 가진다`() {
        val notification = notification()

        assertEquals(1L, notification.recipientUserId)
        assertEquals(2L, notification.actorUserId)
        assertEquals(10L, notification.targetPhotoId)
        assertEquals(NotificationType.COMMENT, notification.notificationType)
        assertEquals("notif-1", notification.notifId)
        assertEquals(1, notification.groupCount)
        assertFalse(notification.isRead)
        assertEquals(sentAt, notification.sentAt)
        assertEquals(0L, notification.id)
    }

    @Test
    fun `그룹 개수는 1 미만일 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            notification(groupCount = 0)
        }

        assertEquals("그룹 개수는 1 이상이어야 합니다.", exception.message)
    }

    @Test
    fun `알림 식별자는 공백일 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            notification(notifId = "  ")
        }

        assertEquals("알림 식별자는 필수입니다.", exception.message)
    }

    @Test
    fun `알림 식별자는 36자를 넘을 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            notification(notifId = "a".repeat(37))
        }

        assertEquals("알림 식별자는 36자 이내여야 합니다.", exception.message)
    }

    @Test
    fun `제목과 본문은 공백일 수 없다`() {
        val titleException = assertThrows<IllegalArgumentException> {
            notification(title = "  ")
        }
        val bodyException = assertThrows<IllegalArgumentException> {
            notification(body = "  ")
        }

        assertEquals("알림 제목은 필수입니다.", titleException.message)
        assertEquals("알림 본문은 필수입니다.", bodyException.message)
    }

    @Test
    fun `마감되지 않았고 5분이 지나지 않았으면 그룹 윈도우는 열려 있다`() {
        val notification = notification()

        assertTrue(notification.isGroupWindowOpen(sentAt.plusMinutes(4).plusSeconds(59)))
    }

    @Test
    fun `정확히 5분이 지난 순간 그룹 윈도우는 닫힌다`() {
        val notification = notification()

        assertFalse(notification.isGroupWindowOpen(sentAt.plusMinutes(5)))
    }

    @Test
    fun `groupClosedAt 이 찍혀 있으면 5분 안이어도 닫혀 있다`() {
        val notification = notification(groupClosedAt = sentAt.plusMinutes(1))

        assertFalse(notification.isGroupWindowOpen(sentAt.plusMinutes(2)))
    }

    @Test
    fun `그룹 개수가 1보다 크면 마감 후속 발송이 필요하다`() {
        assertFalse(notification(groupCount = 1).isGroupSummaryRequired())
        assertTrue(notification(groupCount = 2).isGroupSummaryRequired())
    }

    @Test
    fun `data payload 는 알림 식별자와 종류와 사진과 그룹 개수를 문자열로 담는다`() {
        val notification = notification(
            notifId = "notif-42",
            notificationType = NotificationType.REACTION,
            targetPhotoId = 77L,
            groupCount = 3,
        )

        assertEquals(
            mapOf(
                "notifId" to "notif-42",
                "notifType" to "REACTION",
                "photoId" to "77",
                "groupCount" to "3",
            ),
            notification.dataPayload(),
        )
    }

    @Test
    fun `그룹 윈도우 락 키는 수신자와 사진으로 만들어진다`() {
        assertEquals("notification:group:1:10", Notification.groupWindowLockKey(1L, 10L))
    }

    private fun uploadNotification(
        notifId: String = "notif-upload-1",
        targetPhotoId: Long = 77L,
        targetAddress: String? = "성수동",
        photoCount: Int = 3,
    ) = Notification.upload(
        notifId = notifId,
        recipientUserId = 1L,
        actorUserId = 2L,
        targetPhotoId = targetPhotoId,
        targetAddress = targetAddress,
        photoCount = photoCount,
        title = "새 사진",
        body = "지민님이 사진을 올렸어요",
        sentAt = sentAt,
    )

    /**
     * 🔴 D5 / F13. 이 슬라이스에서 가장 중요한 불변식이다.
     * groupClosedAt 을 null 로 두면 슬라이스3의 NotificationGroupWindowScheduler 가
     * (notification_type 필터가 없으므로) 5분 뒤 이 UPLOAD 알림을 마감 대상으로 집어
     * 본문을 "댓글 N개"로 덮어쓰고 2차 푸시를 보낸다.
     * 방어를 호출자 성실성이 아니라 팩터리 불변식에 맡긴다.
     */
    @Test
    fun `Notification upload로 만든 알림은 groupClosedAt이 sentAt과 같다`() {
        val notification = uploadNotification()

        assertEquals(sentAt, notification.groupClosedAt)
        assertFalse(notification.isGroupWindowOpen(sentAt))
    }

    @Test
    fun `Notification upload는 UPLOAD 종류와 사진 장수를 그대로 싣는다`() {
        val notification = uploadNotification(targetPhotoId = 99L, photoCount = 5)

        assertEquals(NotificationType.UPLOAD, notification.notificationType)
        assertEquals(5, notification.groupCount)
        assertEquals(99L, notification.targetPhotoId)
        assertEquals("성수동", notification.targetAddress)
    }

    /** D4/B18: 단일 장소(주소 non-null)면 그 주소 핀으로 딥링크할 수 있게 키를 하나 더 싣는다. */
    @Test
    fun `targetAddress가 있으면 data payload에 targetAddress 키가 포함된다`() {
        val payload = uploadNotification(notifId = "notif-42", targetPhotoId = 77L, photoCount = 3).dataPayload()

        assertEquals(
            mapOf(
                "notifId" to "notif-42",
                "notifType" to "UPLOAD",
                "photoId" to "77",
                "groupCount" to "3",
                "targetAddress" to "성수동",
            ),
            payload,
        )
    }

    /**
     * 🔴 B18: 기존 N-1 페이로드 불변 계약.
     * targetAddress 를 무조건 넣으면 값이 null 인 N-1 알림의 페이로드에도 키가 생겨
     * 슬라이스3의 계약이 깨진다. non-null 일 때만 조건부로 넣어야 한다.
     */
    @Test
    fun `targetAddress가 없으면 data payload는 키가 4개다`() {
        val payload = uploadNotification(targetAddress = null).dataPayload()

        assertEquals(4, payload.size)
        assertFalse(payload.containsKey("targetAddress"))
    }
}
