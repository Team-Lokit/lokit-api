package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import kr.co.lokit.api.fixture.createPendingUploadNotification
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실 DB(H2 MODE=PostgreSQL, ddl-auto=create-drop) 실측 테스트.
 *
 * 여기서만 검증할 수 있는 것(목으로는 원리적으로 불가):
 * - `photo_ids` 구분자 문자열 라운드트립(D2). 깨지면 자식 테이블로 전환하라는 신호다(되돌리기 신호 2).
 * - 파생 쿼리 이름이 실제로 의도한 WHERE/ORDER BY 로 번역되는가
 *   (`SentAtIsNull`, `ScheduledAtLessThanEqual`, `OrderByScheduledAtAsc`).
 *   특히 `LessThanEqual` 이 `LessThan` 으로 회귀하면 `now == scheduledAt` 인 배치가 영원히 안 나간다(D3).
 * - 더티체킹(`appendPhotoAndReschedule` / `markSent`)이 실제 UPDATE 로 반영되는가.
 * - HQL delete 가 `@SoftDelete` 로 UPDATE 재작성되는가(F16) — 취소한 배치만 사라지고
 *   물리 행은 남으며, 다른 커플/이미 발송된 배치는 영향받지 않는가.
 */
@DataJpaTest
@Import(JpaPendingUploadNotificationRepository::class)
class PendingUploadNotificationRepositoryTest {
    @Autowired
    lateinit var repository: PendingUploadNotificationRepositoryPort

    @Autowired
    lateinit var pendingUploadNotificationJpaRepository: PendingUploadNotificationJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun flushAndClear() {
        pendingUploadNotificationJpaRepository.flush()
        entityManager.clear()
    }

    /** @SoftDelete 필터를 우회해 실제 물리 행 수를 센다. count() 는 소프트삭제된 행을 빼고 세기 때문. */
    private fun rawRowCount(): Long =
        (
            entityManager
                .createNativeQuery("select count(*) from pending_upload_notification")
                .singleResult as Number
        ).toLong()

    @Test
    fun `photo_ids 문자열이 저장 후 그대로 복원된다`() {
        val saved = repository.save(
            createPendingUploadNotification(coupleId = 1L, actorUserId = 2L, photoIds = listOf(1L, 2L, 3L)),
        )
        flushAndClear()

        val reloaded = repository.findUnsentByCoupleAndActor(coupleId = 1L, actorUserId = 2L)

        assertEquals(listOf(1L, 2L, 3L), reloaded?.photoIds)
        // 저장 형태 자체도 못박는다 — JSON/배열 컬럼으로 슬쩍 바뀌면 여기서 죽는다(D2).
        val raw = entityManager
            .createNativeQuery("select photo_ids from pending_upload_notification where id = :id")
            .setParameter("id", saved.id)
            .singleResult
        assertEquals("1,2,3", raw.toString())
    }

    /**
     * 🔴 D3/B11 의 핵심. `now == scheduledAt` 은 반드시 발송 대상이다(N-1 의 isGroupWindowOpen 과 부호 반대).
     * 파생 쿼리가 `LessThan` 으로 회귀하면 (d) 가 빠지고, `SentAtIsNull` 을 빠뜨리면 (c) 가 섞인다.
     */
    @Test
    fun `발송 대상 폴링은 시각 경계와 발송여부를 정확히 가른다`() {
        // (a) 기한 지남 + 미발송 → 대상
        repository.save(
            createPendingUploadNotification(
                coupleId = 1L,
                actorUserId = 2L,
                photoIds = listOf(101L),
                scheduledAt = now.minusMinutes(1),
            ),
        )
        // (b) 아직 기한 전 → 제외
        repository.save(
            createPendingUploadNotification(
                coupleId = 2L,
                actorUserId = 3L,
                photoIds = listOf(102L),
                scheduledAt = now.plusMinutes(1),
            ),
        )
        // (c) 기한은 지났지만 이미 발송됨 → 제외 (포함되면 정렬상 맨 앞에 온다)
        repository.save(
            createPendingUploadNotification(
                coupleId = 3L,
                actorUserId = 4L,
                photoIds = listOf(103L),
                scheduledAt = now.minusMinutes(2),
                sentAt = now.minusMinutes(1),
            ),
        )
        // (d) 정확히 경계 — now == scheduledAt → 반드시 포함
        repository.save(
            createPendingUploadNotification(
                coupleId = 4L,
                actorUserId = 5L,
                photoIds = listOf(104L),
                scheduledAt = now,
            ),
        )
        flushAndClear()

        val targets = repository.findDuePendings(scheduledAtBefore = now, limit = 10)

        // scheduledAt 오름차순이므로 (a) → (d)
        assertEquals(listOf(101L, 104L), targets.map { it.photoIds.single() })
    }

    @Test
    fun `findUnsentByCoupleAndActor는 발송된 배치를 돌려주지 않는다`() {
        repository.save(
            createPendingUploadNotification(
                coupleId = 7L,
                actorUserId = 8L,
                scheduledAt = now.minusMinutes(5),
                sentAt = now,
            ),
        )
        flushAndClear()

        assertNull(repository.findUnsentByCoupleAndActor(coupleId = 7L, actorUserId = 8L))
    }

    @Test
    fun `appendPhotoAndReschedule과 markSent가 실제로 영속화된다`() {
        val saved = repository.save(
            createPendingUploadNotification(
                coupleId = 1L,
                actorUserId = 2L,
                photoIds = listOf(10L),
                scheduledAt = now.plusMinutes(10),
            ),
        )
        flushAndClear()

        repository.appendPhotoAndReschedule(saved.id, listOf(10L, 11L), now.plusMinutes(20))
        flushAndClear()

        // 반환값이 아니라 DB 를 다시 읽는다 — 복사본만 고치고 끝내는 구현은 여기서 죽는다.
        val afterAppend = repository.findUnsentByCoupleAndActor(coupleId = 1L, actorUserId = 2L)
        assertEquals(listOf(10L, 11L), afterAppend?.photoIds)
        assertEquals(now.plusMinutes(20), afterAppend?.scheduledAt)

        repository.markSent(saved.id, now.plusMinutes(21))
        flushAndClear()

        assertNull(repository.findUnsentByCoupleAndActor(coupleId = 1L, actorUserId = 2L))
        assertEquals(
            now.plusMinutes(21),
            pendingUploadNotificationJpaRepository.findById(saved.id).orElseThrow().sentAt,
        )
    }

    /**
     * 🔴 F16 실증. HQL `delete` 는 `@SoftDelete` 때문에 UPDATE 로 재작성된다 —
     * 취소된 배치는 폴링에서 사라지되 물리 행은 남는다. 네이티브 SQL 로 바꾸면 물리 행 수가 줄어 여기서 죽는다.
     */
    @Test
    fun `커플 연결 해제 취소 후 그 배치는 폴링 대상에서 사라진다`() {
        // (a) 커플1 미발송 → 취소 대상
        repository.save(
            createPendingUploadNotification(
                coupleId = 1L,
                actorUserId = 2L,
                photoIds = listOf(201L),
                scheduledAt = now.minusMinutes(1),
            ),
        )
        // (b) 커플1 이미 발송 → 남아야 함
        val sent = repository.save(
            createPendingUploadNotification(
                coupleId = 1L,
                actorUserId = 3L,
                photoIds = listOf(202L),
                scheduledAt = now.minusMinutes(2),
                sentAt = now.minusMinutes(1),
            ),
        )
        // (c) 커플2 미발송 → 남아야 하고 폴링에도 계속 잡혀야 함
        repository.save(
            createPendingUploadNotification(
                coupleId = 2L,
                actorUserId = 4L,
                photoIds = listOf(203L),
                scheduledAt = now.minusMinutes(1),
            ),
        )
        flushAndClear()

        val cancelled = repository.deleteUnsentByCoupleId(1L)
        flushAndClear()

        assertEquals(1, cancelled)
        // 취소한 커플1 배치만 사라지고, 다른 커플의 미발송 배치는 그대로 폴링 대상이다.
        assertEquals(listOf(203L), repository.findDuePendings(scheduledAtBefore = now, limit = 10).map { it.photoIds.single() })
        // 같은 커플이라도 이미 발송된 배치는 건드리지 않는다.
        assertTrue(pendingUploadNotificationJpaRepository.findById(sent.id).isPresent)
        assertNotNull(repository.findUnsentByCoupleAndActor(coupleId = 2L, actorUserId = 4L))
        // 소프트삭제 UPDATE 재작성(F16) — 물리 행은 3건 그대로다.
        assertEquals(3L, rawRowCount())
    }
}
