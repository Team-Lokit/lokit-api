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

    // ─────────────────────────── 슬라이스6: 알림함(inbox) ───────────────────────────

    /** R2 — page/size 가 그대로 PageRequest.of(page, size) 로 간다. Sort 를 얹지 않는다(B16). */
    @Test
    fun `인박스 페이지를 PageRequest로 위임한다`() {
        whenever(
            notificationJpaRepository.findAllByRecipientUserIdOrderBySentAtDescIdDesc(any(), any()),
        ).thenReturn(listOf(entity(notifId = "notif-42")))

        repository.findInboxPage(recipientUserId = 7L, page = 2, size = 5)

        val recipientCaptor = argumentCaptor<Long>()
        val pageableCaptor = argumentCaptor<Pageable>()
        verify(notificationJpaRepository)
            .findAllByRecipientUserIdOrderBySentAtDescIdDesc(
                recipientCaptor.capture(),
                pageableCaptor.capture(),
            )
        assertEquals(7L, recipientCaptor.firstValue)
        assertEquals(PageRequest.of(2, 5), pageableCaptor.firstValue)
    }

    /**
     * R3 — 엔티티가 도메인으로 전 필드 복원되는가. isRead/targetAddress 를 기본값과 다르게 둔다:
     * toDomain 이 그 둘을 흘리면 기본값(false/null)과 구분되지 않아 조용히 통과한다.
     */
    @Test
    fun `인박스 조회 결과를 도메인으로 변환한다`() {
        whenever(
            notificationJpaRepository.findAllByRecipientUserIdOrderBySentAtDescIdDesc(any(), any()),
        ).thenReturn(
            listOf(
                entity(
                    notifId = "notif-42",
                    recipientUserId = 7L,
                    notificationType = NotificationType.REACTION,
                    targetPhotoId = 99L,
                    groupCount = 3,
                    title = "새 반응",
                    body = "지수님이 반응을 남겼어요",
                    isRead = true,
                    sentAt = SENT_AT,
                    groupClosedAt = CLOSED_AT,
                    targetAddress = "성동구 성수동",
                ),
            ),
        )

        val found = repository.findInboxPage(recipientUserId = 7L, page = 0, size = 20).single()

        assertEquals("notif-42", found.notifId)
        assertEquals(7L, found.recipientUserId)
        assertEquals(NotificationType.REACTION, found.notificationType)
        assertEquals(99L, found.targetPhotoId)
        assertEquals(3, found.groupCount)
        assertEquals("새 반응", found.title)
        assertEquals("지수님이 반응을 남겼어요", found.body)
        assertEquals(true, found.isRead)
        assertEquals(SENT_AT, found.sentAt)
        assertEquals(CLOSED_AT, found.groupClosedAt)
        assertEquals("성동구 성수동", found.targetAddress)
    }

    /** R4 — 404/403 구분은 호출자 몫이다. 어댑터는 없으면 예외가 아니라 null 을 돌려준다(계약 2-2). */
    @Test
    fun `notifId로 조회하고 없으면 널을 반환한다`() {
        whenever(notificationJpaRepository.findByNotifId("notif-42"))
            .thenReturn(entity(notifId = "notif-42", recipientUserId = 7L))
        whenever(notificationJpaRepository.findByNotifId("missing")).thenReturn(null)

        assertEquals("notif-42", repository.findByNotifId("notif-42")?.notifId)
        assertNull(repository.findByNotifId("missing"))
    }

    /**
     * R5 — 더티체킹. save(notification.copy(isRead=true)) 로 구현하면 새 행이 INSERT 된다(F-신규-1).
     * never().save(...) 가 그 구현을 잡는다.
     */
    @Test
    fun `읽음 처리는 save를 부르지 않고 엔티티 플래그만 바꾼다`() {
        val existing = entity(isRead = false)
        whenever(notificationJpaRepository.findById(5L)).thenReturn(Optional.of(existing))

        val read = repository.markAsRead(5L)

        assertEquals(true, existing.isRead)
        assertEquals(true, read.isRead)
        verify(notificationJpaRepository, never()).save(any<NotificationEntity>())
    }

    /** R6 — 여기서 난 예외가 서비스를 거쳐 404 로 이어진다. */
    @Test
    fun `읽음 처리 대상이 없으면 entityNotFound를 던진다`() {
        whenever(notificationJpaRepository.findById(5L)).thenReturn(Optional.empty())

        assertThrows<BusinessException.ResourceNotFoundException> {
            repository.markAsRead(5L)
        }
    }

    /** R7 — 빈 결과에 deleteAll(emptyList()) 를 쏘지 않는다. */
    @Test
    fun `정리 대상이 없으면 삭제를 호출하지 않고 0을 반환한다`() {
        whenever(
            notificationJpaRepository.findAllBySentAtBeforeOrderBySentAtAsc(any(), any()),
        ).thenReturn(emptyList())

        assertEquals(0, repository.deleteSentBefore(SENT_AT, 500))

        verify(notificationJpaRepository, never()).deleteAll(any<List<NotificationEntity>>())
    }

    /**
     * R8 — deleteAll(entities) 를 타야 @SoftDelete 가 DELETE 를 is_deleted=true UPDATE 로 재작성한다.
     * 네이티브 삭제로 구현하면 하드삭제가 된다(F-신규-2). 실제 물리 잔존 여부는 R15 가 실측한다.
     */
    @Test
    fun `정리는 조회한 엔티티를 deleteAll로 넘기고 건수를 반환한다`() {
        val targets = listOf(entity(notifId = "old-1"), entity(notifId = "old-2"))
        whenever(
            notificationJpaRepository.findAllBySentAtBeforeOrderBySentAtAsc(any(), any()),
        ).thenReturn(targets)

        val deleted = repository.deleteSentBefore(SENT_AT, 500)

        val sentAtCaptor = argumentCaptor<LocalDateTime>()
        val pageableCaptor = argumentCaptor<Pageable>()
        verify(notificationJpaRepository)
            .findAllBySentAtBeforeOrderBySentAtAsc(sentAtCaptor.capture(), pageableCaptor.capture())
        assertEquals(SENT_AT, sentAtCaptor.firstValue)
        assertEquals(PageRequest.of(0, 500), pageableCaptor.firstValue)

        val deleteCaptor = argumentCaptor<List<NotificationEntity>>()
        verify(notificationJpaRepository).deleteAll(deleteCaptor.capture())
        assertEquals(listOf("old-1", "old-2"), deleteCaptor.firstValue.map { it.notifId })
        assertEquals(2, deleted)
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
        targetAddress: String? = null,
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
        targetAddress = targetAddress,
    )

    companion object {
        private val SENT_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
        private val CLOSED_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 5)
    }
}
