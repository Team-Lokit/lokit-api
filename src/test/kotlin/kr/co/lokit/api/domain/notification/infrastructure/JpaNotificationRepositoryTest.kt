package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.fixture.createNotification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 어댑터의 '위임과 변환'만 검증한다. 쿼리가 실제로 맞는 행을 고르는지(파생 쿼리 이름 해석,
 * @SoftDelete 와의 상호작용, 컬럼 매핑)는 목으로는 원리적으로 검증 불가 — @DataJpaTest 몫이다.
 *
 * F9/B18: BaseEntity.equals 는 id 가 null 이면 항상 false 다. 저장 직전 엔티티는 id 가 null 이므로
 * verify(jpa).save(expectedEntity) 는 절대 통과하지 못한다. 반드시 argumentCaptor 로 받아 필드를 읽는다.
 */
@ExtendWith(MockitoExtension::class)
class JpaNotificationRepositoryTest {
    @Mock
    lateinit var notificationJpaRepository: NotificationJpaRepository

    lateinit var repository: JpaNotificationRepository

    @BeforeEach
    fun setUp() {
        repository = JpaNotificationRepository(notificationJpaRepository)
    }

    @Test
    fun `알림을 저장하면 모든 필드가 엔티티로 옮겨진다`() {
        whenever(notificationJpaRepository.save(any<NotificationEntity>())).thenAnswer { it.arguments[0] }

        repository.save(
            createNotification(
                notifId = "notif-42",
                recipientUserId = 7L,
                actorUserId = 8L,
                notificationType = NotificationType.REACTION,
                targetPhotoId = 99L,
                groupCount = 3,
                title = "새 반응",
                body = "지수님이 반응을 남겼어요",
                isRead = true,
                sentAt = SENT_AT,
                groupClosedAt = CLOSED_AT,
            ),
        )

        val captor = argumentCaptor<NotificationEntity>()
        verify(notificationJpaRepository).save(captor.capture())
        val entity = captor.firstValue
        assertEquals("notif-42", entity.notifId)
        assertEquals(7L, entity.recipientUserId)
        assertEquals(8L, entity.actorUserId)
        assertEquals(NotificationType.REACTION, entity.notificationType)
        assertEquals(99L, entity.targetPhotoId)
        assertEquals(3, entity.groupCount)
        assertEquals("새 반응", entity.title)
        assertEquals("지수님이 반응을 남겼어요", entity.body)
        assertEquals(true, entity.isRead)
        assertEquals(SENT_AT, entity.sentAt)
        assertEquals(CLOSED_AT, entity.groupClosedAt)
    }

    @Test
    fun `마감되지 않은 가장 최근 알림을 조회한다`() {
        whenever(
            notificationJpaRepository
                .findFirstByRecipientUserIdAndTargetPhotoIdAndGroupClosedAtIsNullOrderBySentAtDesc(7L, 99L),
        ).thenReturn(entity(notifId = "notif-42", recipientUserId = 7L, targetPhotoId = 99L, groupCount = 2))

        val found = repository.findLatestUnclosedByRecipientAndPhoto(7L, 99L)

        assertEquals("notif-42", found?.notifId)
        assertEquals(7L, found?.recipientUserId)
        assertEquals(99L, found?.targetPhotoId)
        assertEquals(2, found?.groupCount)
        assertNull(found?.groupClosedAt)
    }

    @Test
    fun `마감되지 않은 알림이 없으면 널을 반환한다`() {
        whenever(
            notificationJpaRepository
                .findFirstByRecipientUserIdAndTargetPhotoIdAndGroupClosedAtIsNullOrderBySentAtDesc(7L, 99L),
        ).thenReturn(null)

        assertNull(repository.findLatestUnclosedByRecipientAndPhoto(7L, 99L))
    }

    @Test
    fun `그룹 개수를 증가시키면 엔티티 필드가 1 늘어난다`() {
        val existing = entity(groupCount = 2)
        whenever(notificationJpaRepository.findById(5L)).thenReturn(Optional.of(existing))

        val increased = repository.increaseGroupCount(5L)

        // 더티체킹이므로 save 를 부르지 않는다 — 영속 인스턴스의 필드가 직접 바뀌어야 한다.
        assertEquals(3, existing.groupCount)
        assertEquals(3, increased.groupCount)
        verify(notificationJpaRepository, never()).save(any<NotificationEntity>())
    }

    @Test
    fun `그룹 증가 대상이 없으면 예외가 발생한다`() {
        whenever(notificationJpaRepository.findById(5L)).thenReturn(Optional.empty())

        assertThrows<BusinessException.ResourceNotFoundException> {
            repository.increaseGroupCount(5L)
        }
    }

    @Test
    fun `마감 대상 조회는 sent_at 상한과 개수 제한을 그대로 전달한다`() {
        whenever(
            notificationJpaRepository
                .findAllByGroupClosedAtIsNullAndSentAtLessThanEqualOrderBySentAtAsc(any(), any()),
        ).thenReturn(listOf(entity(notifId = "notif-42")))

        val found = repository.findClosableGroupWindows(SENT_AT, 200)

        val sentAtCaptor = argumentCaptor<LocalDateTime>()
        val pageableCaptor = argumentCaptor<Pageable>()
        verify(notificationJpaRepository)
            .findAllByGroupClosedAtIsNullAndSentAtLessThanEqualOrderBySentAtAsc(
                sentAtCaptor.capture(),
                pageableCaptor.capture(),
            )
        assertEquals(SENT_AT, sentAtCaptor.firstValue)
        assertEquals(PageRequest.of(0, 200), pageableCaptor.firstValue)
        assertEquals(listOf("notif-42"), found.map { it.notifId })
    }

    @Test
    fun `그룹 윈도우를 마감하면 마감 시각과 본문이 갱신된다`() {
        val existing = entity(body = "상대방님이 댓글을 남겼어요", groupCount = 3)
        whenever(notificationJpaRepository.findById(5L)).thenReturn(Optional.of(existing))

        val closed = repository.closeGroupWindow(5L, CLOSED_AT, "지수님이 댓글 3개를 남겼어요")

        assertEquals(CLOSED_AT, existing.groupClosedAt)
        assertEquals("지수님이 댓글 3개를 남겼어요", existing.body)
        assertEquals(CLOSED_AT, closed.groupClosedAt)
        assertEquals("지수님이 댓글 3개를 남겼어요", closed.body)
        verify(notificationJpaRepository, never()).save(any<NotificationEntity>())
    }

    private fun entity(
        notifId: String = "notif-1",
        recipientUserId: Long = 1L,
        actorUserId: Long = 2L,
        notificationType: NotificationType = NotificationType.COMMENT,
        targetPhotoId: Long = 10L,
        groupCount: Int = 1,
        title: String = "새 댓글",
        body: String = "상대방님이 댓글을 남겼어요",
        isRead: Boolean = false,
        sentAt: LocalDateTime = SENT_AT,
        groupClosedAt: LocalDateTime? = null,
    ) = NotificationEntity(
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
    )

    companion object {
        private val SENT_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
        private val CLOSED_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 5)
    }
}
