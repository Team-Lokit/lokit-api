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
}
