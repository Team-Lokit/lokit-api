package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingUploadNotificationTest {

    /**
     * 시간 픽스처는 고정 상수다. LocalDateTime.now() 기준 상대 시각을 쓰면
     * 실행 시점(자정 경계·서머타임)에 따라 결과가 흔들린다.
     */
    private val now = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun pending(
        id: Long = 1L,
        coupleId: Long = 1L,
        recipientUserId: Long = 1L,
        actorUserId: Long = 2L,
        photoIds: List<Long> = listOf(10L),
        scheduledAt: LocalDateTime = now.plusMinutes(10),
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

    @Test
    fun `새 배치는 사진 한 장과 10분 뒤 발송 예정 시각을 가진다`() {
        val batch = PendingUploadNotification.newBatch(
            coupleId = 1L,
            recipientUserId = 1L,
            actorUserId = 2L,
            photoId = 10L,
            now = now,
        )

        assertEquals(listOf(10L), batch.photoIds)
        assertEquals(now.plusMinutes(10), batch.scheduledAt)
        assertNull(batch.sentAt)
        assertEquals(0L, batch.id)
    }

    @Test
    fun `대상 사진이 없으면 배치를 만들 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            pending(photoIds = emptyList())
        }

        assertEquals("대상 사진이 최소 1장 있어야 합니다.", exception.message)
    }

    @Test
    fun `자기 자신에게는 알림을 예약할 수 없다`() {
        val exception = assertThrows<IllegalArgumentException> {
            pending(recipientUserId = 2L, actorUserId = 2L)
        }

        assertEquals("자기 자신에게는 알림을 예약할 수 없습니다.", exception.message)
    }

    @Test
    fun `사진을 추가하면 목록에 쌓이고 발송 예정 시각이 10분 뒤로 다시 밀린다`() {
        val batch = pending(photoIds = listOf(10L), scheduledAt = now.plusMinutes(10))

        val reset = batch.withPhoto(photoId = 11L, now = now.plusMinutes(3))

        assertEquals(listOf(10L, 11L), reset.photoIds)
        assertEquals(now.plusMinutes(13), reset.scheduledAt)
    }

    @Test
    fun `같은 사진을 다시 추가해도 목록은 늘지 않는다`() {
        val batch = pending(photoIds = listOf(10L))

        val reset = batch.withPhoto(photoId = 10L, now = now.plusMinutes(3))

        assertEquals(listOf(10L), reset.photoIds)
    }

    @Test
    fun `상한을 넘으면 가장 오래된 사진부터 버려 상한을 지킨다`() {
        val full = pending(photoIds = (1L..PendingUploadNotification.MAX_PHOTO_IDS.toLong()).toList())

        val reset = full.withPhoto(photoId = 999L, now = now)

        assertEquals(PendingUploadNotification.MAX_PHOTO_IDS, reset.photoIds.size)
        assertEquals(2L, reset.photoIds.first())
        assertEquals(999L, reset.photoIds.last())
    }

    @Test
    fun `발송 예정 시각과 같은 순간은 발송 대상이다`() {
        val batch = pending(scheduledAt = now.plusMinutes(10))

        assertTrue(batch.isDue(now.plusMinutes(10)))
    }

    @Test
    fun `발송 예정 시각 직전은 아직 발송 대상이 아니다`() {
        val batch = pending(scheduledAt = now.plusMinutes(10))

        assertFalse(batch.isDue(now.plusMinutes(10).minusNanos(1)))
    }

    @Test
    fun `이미 발송된 배치는 예정 시각이 지나도 발송 대상이 아니다`() {
        val batch = pending(scheduledAt = now.plusMinutes(10), sentAt = now.plusMinutes(10))

        assertTrue(batch.isSent())
        assertFalse(batch.isDue(now.plusHours(1)))
    }

    @Test
    fun `사진 목록은 구분자 문자열로 인코딩되고 그대로 복원된다`() {
        val ids = listOf(10L, 11L, 12L)

        val encoded = PendingUploadNotification.encodePhotoIds(ids)

        assertEquals("10,11,12", encoded)
        assertEquals(ids, PendingUploadNotification.decodePhotoIds(encoded))
    }

    @Test
    fun `빈 문자열을 복원하면 빈 목록이 된다`() {
        assertEquals(emptyList(), PendingUploadNotification.decodePhotoIds(""))
    }

    @Test
    fun `주소가 전부 같으면 그 주소가 공통 주소다`() {
        assertEquals("서울시 강남구", PendingUploadNotification.commonAddressOf(listOf("서울시 강남구", "서울시 강남구")))
    }

    @Test
    fun `주소가 하나라도 다르거나 널이 섞이거나 비어 있으면 공통 주소는 없다`() {
        assertNull(PendingUploadNotification.commonAddressOf(listOf("서울시 강남구", "부산시 해운대구")))
        assertNull(PendingUploadNotification.commonAddressOf(listOf("서울시 강남구", null)))
        assertNull(PendingUploadNotification.commonAddressOf(emptyList()))
    }

    @Test
    fun `락 키는 커플과 행위자로 만들어진다`() {
        assertEquals("notification:upload:1:2", PendingUploadNotification.lockKey(1L, 2L))
    }
}
