package kr.co.lokit.api.domain.notification.infrastructure

import kr.co.lokit.api.fixture.createPendingUploadNotification
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
 * 어댑터의 '위임과 변환'만 검증한다. 파생 쿼리가 실제로 맞는 행을 고르는지, @SoftDelete 와의
 * 상호작용, photo_ids 컬럼 라운드트립은 목으로는 원리적으로 검증 불가 — @DataJpaTest 몫이다(T8).
 *
 * F15: BaseEntity.equals 는 id 가 null 이면 항상 false 다. 저장 직전 엔티티는 id 가 null 이므로
 * verify(jpa).save(expectedEntity) 는 절대 통과하지 못한다. 반드시 argumentCaptor 로 받아 필드를 읽는다.
 */
@ExtendWith(MockitoExtension::class)
class JpaPendingUploadNotificationRepositoryTest {
    @Mock
    lateinit var pendingUploadNotificationJpaRepository: PendingUploadNotificationJpaRepository

    lateinit var repository: JpaPendingUploadNotificationRepository

    @BeforeEach
    fun setUp() {
        repository = JpaPendingUploadNotificationRepository(pendingUploadNotificationJpaRepository)
    }

    @Test
    fun `대기 배치를 저장하면 사진 목록이 문자열로 인코딩되어 엔티티로 옮겨진다`() {
        whenever(pendingUploadNotificationJpaRepository.save(any<PendingUploadNotificationEntity>()))
            .thenAnswer { it.arguments[0] }

        repository.save(
            createPendingUploadNotification(
                coupleId = 7L,
                recipientUserId = 8L,
                actorUserId = 9L,
                photoIds = listOf(10L, 20L, 30L),
                scheduledAt = SCHEDULED_AT,
                sentAt = null,
            ),
        )

        val captor = argumentCaptor<PendingUploadNotificationEntity>()
        verify(pendingUploadNotificationJpaRepository).save(captor.capture())
        val entity = captor.firstValue
        // D2: JSON/Converter 선례 0건(F17) — 구분자 문자열이 유일한 저장 형태다.
        assertEquals("10,20,30", entity.photoIds)
        assertEquals(7L, entity.coupleId)
        assertEquals(8L, entity.recipientUserId)
        assertEquals(9L, entity.actorUserId)
        assertEquals(SCHEDULED_AT, entity.scheduledAt)
        assertNull(entity.sentAt)
    }

    @Test
    fun `appendPhotoAndReschedule은 save를 부르지 않는다`() {
        val existing = entity(photoIds = "10", scheduledAt = SCHEDULED_AT)
        whenever(pendingUploadNotificationJpaRepository.findById(5L)).thenReturn(Optional.of(existing))

        val updated = repository.appendPhotoAndReschedule(5L, listOf(10L, 20L), RESCHEDULED_AT)

        // 더티체킹이므로 save 를 부르지 않는다 — 영속 인스턴스의 필드가 직접 바뀌어야 한다.
        assertEquals("10,20", existing.photoIds)
        assertEquals(RESCHEDULED_AT, existing.scheduledAt)
        assertEquals(listOf(10L, 20L), updated.photoIds)
        assertEquals(RESCHEDULED_AT, updated.scheduledAt)
        verify(pendingUploadNotificationJpaRepository, never()).save(any<PendingUploadNotificationEntity>())
    }

    @Test
    fun `findDuePendings는 PageRequest에 Sort를 얹지 않는다`() {
        whenever(
            pendingUploadNotificationJpaRepository
                .findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(any(), any()),
        ).thenReturn(listOf(entity(photoIds = "10,20")))

        val found = repository.findDuePendings(SCHEDULED_AT, 200)

        val scheduledAtCaptor = argumentCaptor<LocalDateTime>()
        val pageableCaptor = argumentCaptor<Pageable>()
        verify(pendingUploadNotificationJpaRepository)
            .findAllBySentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                scheduledAtCaptor.capture(),
                pageableCaptor.capture(),
            )
        // D3/B11: 상한은 now 그 자체다. 어댑터가 여기서 빼거나 더하면 안 된다.
        assertEquals(SCHEDULED_AT, scheduledAtCaptor.firstValue)
        // 정렬은 파생 쿼리 이름(OrderByScheduledAtAsc)이 책임진다 — Pageable 에 Sort 를 겹치면 안 된다.
        assertEquals(PageRequest.of(0, 200), pageableCaptor.firstValue)
        assertEquals(listOf(listOf(10L, 20L)), found.map { it.photoIds })
    }

    @Test
    fun `커플의 미발송 배치를 취소하면 삭제 건수를 그대로 돌려준다`() {
        whenever(pendingUploadNotificationJpaRepository.deleteUnsentByCoupleId(7L)).thenReturn(3)

        val cancelled = repository.deleteUnsentByCoupleId(7L)

        assertEquals(3, cancelled)
    }

    private fun entity(
        coupleId: Long = 1L,
        recipientUserId: Long = 1L,
        actorUserId: Long = 2L,
        photoIds: String = "10",
        scheduledAt: LocalDateTime = SCHEDULED_AT,
        sentAt: LocalDateTime? = null,
    ) = PendingUploadNotificationEntity(
        coupleId = coupleId,
        recipientUserId = recipientUserId,
        actorUserId = actorUserId,
        photoIds = photoIds,
        scheduledAt = scheduledAt,
        sentAt = sentAt,
    )

    companion object {
        private val SCHEDULED_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 10)
        private val RESCHEDULED_AT: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 15)
    }
}
