package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.fixture.createNotification
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실 DB(H2 MODE=PostgreSQL, ddl-auto=create-drop) 실측 테스트.
 *
 * 여기서만 검증할 수 있는 것(목으로는 원리적으로 불가):
 * - 파생 쿼리 이름이 실제로 의도한 WHERE 절로 번역되는가 (`GroupClosedAtIsNull`, `SentAtLessThanEqual`)
 * - **쿼리에 `group_count > 1` 조건이 섞여 있지 않은가** — 계약 D2/B16 의 핵심.
 *   groupCount==1 인 만료 윈도우도 반드시 마감 대상이어야 폴링에서 빠진다.
 *   조건이 끼면 그 행은 영원히 group_closed_at is null 로 남아 매분 재조회된다.
 * - 더티체킹이 실제로 UPDATE 로 반영되는가 (save 미호출 구현의 실증)
 * - Kotlin `is` 접두 프로퍼티가 `is_read` 컬럼으로 매핑되는가 (`read` 로 잘리는 함정)
 */
@DataJpaTest
@Import(JpaNotificationRepository::class)
class NotificationRepositoryTest {
    @Autowired
    lateinit var repository: NotificationRepositoryPort

    @Autowired
    lateinit var notificationJpaRepository: NotificationJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun flushAndClear() {
        notificationJpaRepository.flush()
        entityManager.clear()
    }

    /** @SoftDelete 필터를 우회해 실제 물리 행 수를 센다. count() 는 소프트삭제된 행을 빼고 세기 때문. */
    private fun rawRowCount(): Long =
        (entityManager.createNativeQuery("select count(*) from notification").singleResult as Number).toLong()

    @Test
    fun `마감되지 않은 알림 중 가장 최근 한 건만 돌려준다`() {
        // 마감된 행이 sent_at 기준으로 더 최신이다 — is-null 필터를 빠뜨린 구현을 잡기 위함.
        repository.save(
            createNotification(
                notifId = "closed-late",
                recipientUserId = 1L,
                targetPhotoId = 10L,
                sentAt = now.minusMinutes(1),
                groupClosedAt = now,
            ),
        )
        repository.save(
            createNotification(
                notifId = "closed-early",
                recipientUserId = 1L,
                targetPhotoId = 10L,
                sentAt = now.minusMinutes(10),
                groupClosedAt = now,
            ),
        )
        repository.save(
            createNotification(
                notifId = "open",
                recipientUserId = 1L,
                targetPhotoId = 10L,
                sentAt = now.minusMinutes(5),
                groupClosedAt = null,
            ),
        )
        flushAndClear()

        val found = repository.findLatestUnclosedByRecipientAndPhoto(1L, 10L)

        assertEquals("open", found?.notifId)
    }

    /**
     * 🔴 이 슬라이스에서 가장 중요한 회귀 방지 테스트.
     * (d) 는 groupCount==1 이라 후속 요약 푸시는 필요 없지만 **마감 대상에는 반드시 포함**돼야 한다.
     * 발송 여부는 코드가 분기하고(D2), 쿼리는 group_count 를 보지 않는다.
     */
    @Test
    fun `5분이 지나고 아직 마감되지 않은 윈도우만 배치 대상이 된다`() {
        // (a) 만료 + 미마감 → 대상
        repository.save(
            createNotification(
                notifId = "a",
                sentAt = now.minusMinutes(6),
                groupCount = 3,
                groupClosedAt = null,
            ),
        )
        // (b) 만료 + 이미 마감 → 제외
        repository.save(
            createNotification(
                notifId = "b",
                sentAt = now.minusMinutes(6),
                groupCount = 3,
                groupClosedAt = now.minusMinutes(1),
            ),
        )
        // (c) 미만료 → 제외
        repository.save(
            createNotification(
                notifId = "c",
                sentAt = now.minusMinutes(2),
                groupCount = 2,
                groupClosedAt = null,
            ),
        )
        // (d) 만료 + 미마감 + groupCount==1 → 반드시 대상 (D2/B16)
        repository.save(
            createNotification(
                notifId = "d",
                sentAt = now.minusMinutes(7),
                groupCount = 1,
                groupClosedAt = null,
            ),
        )
        flushAndClear()

        val targets = repository.findClosableGroupWindows(sentAtBefore = now.minusMinutes(5), limit = 10)

        assertEquals(listOf("d", "a"), targets.map { it.notifId })
    }

    /**
     * 🔴 슬라이스4 최고 중요 테스트 (F13 / D5 / B10).
     *
     * `findClosableGroupWindows` 에는 `notification_type` 필터가 없다. N-2 업로드 알림을
     * `groupClosedAt = null` 로 저장하면 슬라이스3 의 마감 배치가 5분 뒤 이 알림을 집어
     * 본문을 "댓글 N개" 로 덮어쓰고 2차 푸시를 보낸다 — 크로스슬라이스 버그다.
     *
     * 방어는 쿼리가 아니라 **도메인 팩터리 불변식**(`Notification.upload` 가 groupClosedAt=sentAt 을
     * 항상 채움)이다(D5). 여기서 실 DB 로 그 불변식이 실제로 폴링을 빠져나가게 하는지 실측한다.
     *
     * 비교군(N-1 댓글 알림, groupClosedAt=null)을 같은 시각에 함께 저장한다 —
     * 이게 잡혀야 "폴링 자체가 고장난 게 아니라 UPLOAD 만 제외됐다" 가 증명된다.
     *
     * 이 테스트가 빨개지면(업로드 알림이 잡히면) 계약 7절 되돌리기 신호 1 이다:
     * 쿼리에 `notification_type` 필터를 덧대는 땜질 금지.
     */
    @Test
    fun `업로드 알림은 그룹 윈도우 마감 폴링에 잡히지 않는다`() {
        repository.save(
            Notification.upload(
                notifId = "upload-1",
                recipientUserId = 1L,
                actorUserId = 2L,
                targetPhotoId = 10L,
                targetAddress = "서울 강남구 테헤란로",
                photoCount = 3,
                title = "새로운 추억",
                body = "상대방님이 서울 강남구 테헤란로에 사진 3장을 올렸어요",
                sentAt = now,
            ),
        )
        // 비교군: 같은 sentAt 의 N-1 댓글 알림. 이건 반드시 잡혀야 한다.
        repository.save(
            createNotification(
                notifId = "comment-1",
                recipientUserId = 3L,
                targetPhotoId = 20L,
                sentAt = now,
                groupClosedAt = null,
            ),
        )
        flushAndClear()

        val targets = repository.findClosableGroupWindows(sentAtBefore = now.plusMinutes(10), limit = 10)

        assertEquals(listOf("comment-1"), targets.map { it.notifId })
    }

    /**
     * 🔴 계약 2-14 (D4 / B18).
     *
     * `Notification.targetAddress` 는 도메인에만 있고 `NotificationEntity` 에는 컬럼이 없다.
     * 그래서 지금은 저장해도 **예외 없이 조용히 유실**된다 — 딥링크 힌트가 사라져도 아무도
     * 안 죽는 종류의 결함이라 목 테스트로는 원리적으로 잡히지 않는다. 실 DB 왕복만이 잡는다.
     *
     * 대조군으로 `targetAddress == null` 인 N-1 댓글 알림을 같이 왕복시킨다 —
     * 이게 함께 초록이어야 "널이 널로 오는 게 아니라 값이 값으로 온다" 가 증명된다.
     * 대조군만 보고 통과 판정하면 컬럼을 아예 안 만들어도 초록이 된다.
     */
    @Test
    fun `target_address가 저장 후 그대로 복원된다`() {
        val upload = repository.save(
            Notification.upload(
                notifId = "upload-addr",
                recipientUserId = 1L,
                actorUserId = 2L,
                targetPhotoId = 10L,
                targetAddress = "성동구 성수동",
                photoCount = 1,
                title = "새로운 추억",
                body = "상대방님이 성동구 성수동에 새로운 추억을 남겼어요",
                sentAt = now,
            ),
        )
        // 대조군: 주소가 없는 N-1 알림(D4에서 null = 여러 장소 또는 댓글 알림).
        val comment = repository.save(
            createNotification(
                notifId = "comment-addr",
                recipientUserId = 3L,
                targetPhotoId = 20L,
                sentAt = now,
                groupClosedAt = now,
                targetAddress = null,
            ),
        )
        flushAndClear()

        assertEquals("성동구 성수동", reload(upload).targetAddress)
        assertNull(reload(comment).targetAddress)
    }

    /**
     * 포트에 단건 조회가 없다. `closeGroupWindow` 만이 id 로 되읽어 `toDomain()` 을 돌려주는
     * 경로라 재조회 수단으로 쓴다 — 지금 들어 있는 값과 같은 값을 다시 넣으므로
     * 관찰 가능한 상태는 바뀌지 않는다(두 픽스처 모두 groupClosedAt 이 이미 non-null 이다).
     */
    private fun reload(saved: Notification): Notification =
        repository.closeGroupWindow(saved.id, saved.groupClosedAt!!, saved.body)

    @Test
    fun `그룹 개수 증가와 마감이 실제로 영속화된다`() {
        val saved = repository.save(createNotification(notifId = "persist-1", groupCount = 1))
        flushAndClear()

        repository.increaseGroupCount(saved.id)
        flushAndClear()

        // 반환값이 아니라 DB 를 다시 읽는다 — 복사본에 +1 하고 끝내는 구현은 여기서 죽는다.
        assertEquals(2, notificationJpaRepository.findById(saved.id).orElseThrow().groupCount)

        val closedAt = now.plusMinutes(5)
        repository.closeGroupWindow(saved.id, closedAt, "상대방님이 댓글 2개를 남겼어요")
        flushAndClear()

        val reloaded = notificationJpaRepository.findById(saved.id).orElseThrow()
        assertEquals(closedAt, reloaded.groupClosedAt)
        assertEquals("상대방님이 댓글 2개를 남겼어요", reloaded.body)
    }

    @Test
    fun `읽음 여부 기본값은 거짓이고 그대로 저장된다`() {
        val saved = repository.save(createNotification(notifId = "read-flag", isRead = false))
        flushAndClear()

        // 컬럼명을 직접 지목한다. Kotlin `is` 접두 프로퍼티가 `read` 로 매핑되면 이 쿼리 자체가 깨진다.
        val raw = entityManager
            .createNativeQuery("select is_read from notification where id = :id")
            .setParameter("id", saved.id)
            .singleResult

        val isReadColumn = when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> error("예상치 못한 is_read 컬럼 타입: ${raw?.javaClass}")
        }
        assertFalse(isReadColumn)
    }

    // ─────────────────────────── 슬라이스6: 알림함(inbox) ───────────────────────────

    /** R9 — 파생 쿼리 이름이 실제로 recipient WHERE + sent_at DESC 로 번역되는가. */
    @Test
    fun `인박스는 수신자 본인 알림만 최신순으로 돌려준다`() {
        repository.save(createNotification(notifId = "mine-old", recipientUserId = 1L, sentAt = now.minusDays(1)))
        repository.save(createNotification(notifId = "mine-new", recipientUserId = 1L, sentAt = now))
        // 남의 알림이 전체에서 가장 최신이다 — recipient 필터가 빠진 구현은 여기서 죽는다.
        repository.save(createNotification(notifId = "others", recipientUserId = 2L, sentAt = now.plusDays(1)))
        flushAndClear()

        val inbox = repository.findInboxPage(recipientUserId = 1L, page = 0, size = 10)

        assertEquals(listOf("mine-new", "mine-old"), inbox.map { it.notifId })
    }

    /**
     * 🔴 R10 — 계약 D2 되돌리기 신호.
     * 마감 전(group_closed_at is null) 알림도 목록에 나와야 한다. 푸시를 탭하고 5분 안에
     * 인박스를 열면 방금 받은 알림이 거기 있어야 한다. 쿼리에 group_closed_at 조건을 끼우면
     * 이 테스트가 빨개진다 — 그때 조건을 제거한다. 필터를 옵션 파라미터로 덧대는 땜질 금지.
     */
    @Test
    fun `인박스는 그룹 윈도우가 열린 알림도 포함한다`() {
        repository.save(
            createNotification(
                notifId = "open",
                recipientUserId = 1L,
                sentAt = now,
                groupCount = 3,
                groupClosedAt = null,
            ),
        )
        repository.save(
            createNotification(
                notifId = "closed",
                recipientUserId = 1L,
                sentAt = now.minusHours(1),
                groupClosedAt = now.minusMinutes(55),
            ),
        )
        flushAndClear()

        val inbox = repository.findInboxPage(recipientUserId = 1L, page = 0, size = 10)

        assertEquals(listOf("open", "closed"), inbox.map { it.notifId })
        // 문구는 저장된 그대로다(D1) — 조회 시점에 groupCount 로 재계산하지 않는다.
        assertEquals("상대방님이 댓글을 남겼어요", inbox.first().body)
        assertEquals(3, inbox.first().groupCount)
    }

    /**
     * R11 — 동률 시 id 내림차순 2차 정렬(B7/F-신규-7). 없으면 offset 페이지 경계에서 행이 중복/누락된다.
     * 픽스처의 sentAt 기본값이 고정 상수라 override 없이 저장하면 세 건이 그대로 동순위가 된다(E22).
     *
     * 이 테스트는 컨텍스트 로딩 단계도 겸해서 지킨다: OrderBySentAtDescIdDesc 의 Id 는
     * BaseEntity(mapped superclass) 프로퍼티라 파생쿼리 해석이 실패하면 PropertyReferenceException 이
     * 기동 시점에 난다(F-신규-11). 그러면 @Query 로 대체한다.
     */
    @Test
    fun `sent_at이 같으면 id 내림차순으로 안정 정렬된다`() {
        repository.save(createNotification(notifId = "tie-1", recipientUserId = 1L))
        repository.save(createNotification(notifId = "tie-2", recipientUserId = 1L))
        repository.save(createNotification(notifId = "tie-3", recipientUserId = 1L))
        flushAndClear()

        val firstPage = repository.findInboxPage(recipientUserId = 1L, page = 0, size = 2)
        val secondPage = repository.findInboxPage(recipientUserId = 1L, page = 1, size = 2)

        assertEquals(listOf("tie-3", "tie-2"), firstPage.map { it.notifId })
        assertEquals(listOf("tie-1"), secondPage.map { it.notifId })
    }

    /**
     * 🔴 R12 — 되돌리기 신호. 더티체킹이 진짜 UPDATE 를 내는지 네이티브로 실측한다.
     * 반환값만 보면 복사본에 true 를 얹고 끝내는 구현도 초록이 된다.
     * 컬럼명을 직접 지목하므로 Kotlin `is` 접두 프로퍼티가 `read` 로 잘리는 함정도 함께 막는다.
     */
    @Test
    fun `읽음 처리가 실제 is_read 컬럼에 반영된다`() {
        val saved = repository.save(createNotification(notifId = "to-read", isRead = false))
        flushAndClear()

        repository.markAsRead(saved.id)
        flushAndClear()

        assertTrue(rawIsRead(saved.id))
    }

    /**
     * R13 — 멱등의 물리적 증명. 이미 true 인 필드에 true 를 다시 넣으면 Hibernate 더티체크가
     * 변화 없음을 보고 UPDATE 를 내지 않는다 → @Version 이 오르지 않는다.
     * 버전이 오르면 매번 읽음 API 호출이 쓸데없는 UPDATE 를 내고 있다는 뜻이다.
     */
    @Test
    fun `이미 읽은 알림에 다시 읽음 처리해도 버전이 오르지 않는다`() {
        val saved = repository.save(createNotification(notifId = "already-read", isRead = true))
        flushAndClear()
        val versionBefore = notificationJpaRepository.findById(saved.id).orElseThrow().version
        flushAndClear()

        repository.markAsRead(saved.id)
        flushAndClear()

        assertTrue(rawIsRead(saved.id))
        assertEquals(versionBefore, notificationJpaRepository.findById(saved.id).orElseThrow().version)
    }

    /**
     * 🔴 R14 — 되돌리기 신호. 경계는 strictly before 다.
     * SentAtLessThanEqual 로 복사하면 정확히 컷오프 시각인 행이 한 건 더 지워진다(F-신규-3).
     */
    @Test
    fun `30일이 지난 알림만 소프트삭제되고 경계 건은 남는다`() {
        val cutoff = Notification.retentionCutoff(now)
        repository.save(createNotification(notifId = "expired", recipientUserId = 1L, sentAt = cutoff.minusDays(1)))
        repository.save(createNotification(notifId = "boundary", recipientUserId = 1L, sentAt = cutoff))
        repository.save(createNotification(notifId = "fresh", recipientUserId = 1L, sentAt = now))
        flushAndClear()

        val deleted = repository.deleteSentBefore(sentAtBefore = cutoff, limit = 500)
        flushAndClear()

        assertEquals(1, deleted)
        assertEquals(
            listOf("fresh", "boundary"),
            repository.findInboxPage(recipientUserId = 1L, page = 0, size = 10).map { it.notifId },
        )
    }

    /**
     * 🔴 R15 — 되돌리기 신호. 정리는 소프트삭제여야 한다(D5).
     * deleteAll(entities) 로 구현하면 Hibernate 가 DELETE 를 is_deleted=true UPDATE 로 재작성하므로
     * 물리 행은 남는다. 네이티브 DELETE 로 구현하면 여기서만 죽는다 — count() 는 두 구현을 구분하지 못한다.
     */
    @Test
    fun `소프트삭제된 알림 행은 물리적으로 남아 있다`() {
        val cutoff = Notification.retentionCutoff(now)
        repository.save(createNotification(notifId = "expired", recipientUserId = 1L, sentAt = cutoff.minusDays(1)))
        repository.save(createNotification(notifId = "fresh", recipientUserId = 1L, sentAt = now))
        flushAndClear()

        repository.deleteSentBefore(sentAtBefore = cutoff, limit = 500)
        flushAndClear()

        assertEquals(1L, notificationJpaRepository.count())
        assertEquals(2L, rawRowCount())
    }

    /** R16 — countInbox 가 @SoftDelete 필터를 타는가. 정리된 알림이 페이지 메타에 남으면 totalPages 가 틀어진다. */
    @Test
    fun `인박스 카운트는 소프트삭제된 알림을 세지 않는다`() {
        val cutoff = Notification.retentionCutoff(now)
        repository.save(createNotification(notifId = "expired", recipientUserId = 1L, sentAt = cutoff.minusDays(1)))
        repository.save(createNotification(notifId = "mine-1", recipientUserId = 1L, sentAt = now))
        repository.save(createNotification(notifId = "mine-2", recipientUserId = 1L, sentAt = now.minusDays(1)))
        repository.save(createNotification(notifId = "others", recipientUserId = 2L, sentAt = now))
        flushAndClear()

        repository.deleteSentBefore(sentAtBefore = cutoff, limit = 500)
        flushAndClear()

        assertEquals(2L, repository.countInbox(1L))
    }

    private fun rawIsRead(id: Long): Boolean {
        val raw = entityManager
            .createNativeQuery("select is_read from notification where id = :id")
            .setParameter("id", id)
            .singleResult
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> error("예상치 못한 is_read 컬럼 타입: ${raw?.javaClass}")
        }
    }
}
