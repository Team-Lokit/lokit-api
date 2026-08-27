package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.concurrency.LockManager
import kr.co.lokit.api.domain.notification.application.port.PendingUploadNotificationRepositoryPort
import kr.co.lokit.api.domain.notification.domain.Notification
import kr.co.lokit.api.domain.notification.domain.PendingUploadNotification
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.user.application.port.UserRepositoryPort
import kr.co.lokit.api.fixture.createPendingUploadNotification
import kr.co.lokit.api.fixture.createPhoto
import kr.co.lokit.api.fixture.createUser
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @InjectMocks 를 쓰지 않는다(계약 T10). LockManager 는 목이 아니라 실 인스턴스여야
 * withLock 의 람다가 실제로 실행된다 — 목으로 두면 schedule 본문이 아예 돌지 않아 검증이 무의미해진다.
 * (슬라이스3 NotificationDispatchServiceTest 와 같은 수동 조립 패턴)
 */
@ExtendWith(MockitoExtension::class)
class UploadNotificationServiceTest {
    @Mock
    lateinit var pendingRepository: PendingUploadNotificationRepositoryPort

    @Mock
    lateinit var photoRepository: PhotoRepositoryPort

    @Mock
    lateinit var userRepository: UserRepositoryPort

    @Mock
    lateinit var notificationDispatchService: NotificationDispatchService

    lateinit var service: UploadNotificationService

    private val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)

    @BeforeEach
    fun setUp() {
        service =
            UploadNotificationService(
                pendingRepository,
                photoRepository,
                userRepository,
                notificationDispatchService,
                LockManager(),
            )
    }

    @Test
    fun `열린 배치가 없으면 새 배치를 저장한다`() {
        whenever(pendingRepository.findUnsentByCoupleAndActor(1L, 2L)).thenReturn(null)
        whenever(pendingRepository.save(any())).thenAnswer { it.getArgument<PendingUploadNotification>(0) }

        service.schedule(coupleId = 1L, recipientUserId = 1L, actorUserId = 2L, photoId = 10L, now = now)

        val captor = argumentCaptor<PendingUploadNotification>()
        verify(pendingRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals(1L, saved.coupleId)
        assertEquals(1L, saved.recipientUserId)
        assertEquals(2L, saved.actorUserId)
        assertEquals(listOf(10L), saved.photoIds)
        assertEquals(now.plusMinutes(10), saved.scheduledAt)
    }

    /**
     * 🔴 핵심(B6/D3): 열린 배치가 있으면 새 행을 만들지 않고 기존 행에 사진을 덧붙이며,
     * scheduledAt 을 now+10분으로 **재대입**한다. 남은 시간을 연장하는 게 아니라 마감기한 자체를 밀어낸다.
     */
    @Test
    fun `열린 배치가 있으면 save를 부르지 않고 사진을 추가하며 타이머를 리셋한다`() {
        val existing =
            createPendingUploadNotification(
                id = 7L,
                coupleId = 1L,
                recipientUserId = 1L,
                actorUserId = 2L,
                photoIds = listOf(10L),
                scheduledAt = now.minusMinutes(3),
            )
        whenever(pendingRepository.findUnsentByCoupleAndActor(1L, 2L)).thenReturn(existing)
        whenever(pendingRepository.appendPhotoAndReschedule(any(), any(), any()))
            .thenAnswer {
                existing.copy(
                    photoIds = it.getArgument(1),
                    scheduledAt = it.getArgument(2),
                )
            }

        service.schedule(coupleId = 1L, recipientUserId = 1L, actorUserId = 2L, photoId = 20L, now = now)

        verify(pendingRepository, never()).save(any())
        val idCaptor = argumentCaptor<Long>()
        val photoIdsCaptor = argumentCaptor<List<Long>>()
        val scheduledAtCaptor = argumentCaptor<LocalDateTime>()
        verify(pendingRepository)
            .appendPhotoAndReschedule(idCaptor.capture(), photoIdsCaptor.capture(), scheduledAtCaptor.capture())
        assertEquals(7L, idCaptor.firstValue)
        assertTrue(photoIdsCaptor.firstValue.contains(20L))
        assertEquals(listOf(10L, 20L), photoIdsCaptor.firstValue)
        assertEquals(now.plusMinutes(10), scheduledAtCaptor.firstValue)
    }

    @Test
    fun `cancelByCoupleId는 포트로 위임하고 삭제 건수를 돌려준다`() {
        whenever(pendingRepository.deleteUnsentByCoupleId(1L)).thenReturn(3)

        assertEquals(3, service.cancelByCoupleId(1L))

        verify(pendingRepository).deleteUnsentByCoupleId(1L)
    }

    /**
     * 🔴 핵심(B7/D3): 스케줄러가 폴링으로 집어온 배치는 이미 낡았을 수 있다. 마감 처리 도중
     * '막차 업로드'가 들어와 타이머가 리셋됐다면 지금 보내면 안 된다 — 락 안에서 재조회한
     * scheduledAt 이 미래면 claim(markSent)도 발송도 하지 않고 조용히 물러난다.
     * 재조회 없이 인자로 받은 pending 의 isDue 만 믿으면 리셋이 스케줄러에게 지고,
     * 사용자는 debounce 가 끝나기 전에 알림을 받는다.
     */
    @Test
    fun `fire는 락 안에서 재조회해 isDue가 아니면 아무것도 하지 않는다`() {
        val pending = duePending()
        val resetInLock = pending.copy(scheduledAt = now.plusMinutes(10))
        whenever(pendingRepository.findUnsentByCoupleAndActor(1L, 2L)).thenReturn(resetInLock)

        val result = service.fire(pending, now)

        assertNull(result)
        verify(pendingRepository, never()).markSent(any(), any())
        verify(notificationDispatchService, never()).dispatchImmediately(any())
    }

    /**
     * 커버리지 리뷰(슬라이스8)로 못박은 경로: 폴링과 fire 사이에 배치가 취소/삭제돼
     * 재조회 결과 자체가 없는 경우도 isDue 불일치와 동일하게 조용히 아무것도 하지 않아야 한다.
     */
    @Test
    fun `fire는 락 안에서 재조회했을 때 배치가 사라졌으면 아무것도 하지 않는다`() {
        val pending = duePending()
        whenever(pendingRepository.findUnsentByCoupleAndActor(1L, 2L)).thenReturn(null)

        val result = service.fire(pending, now)

        assertNull(result)
        verify(pendingRepository, never()).markSent(any(), any())
        verify(notificationDispatchService, never()).dispatchImmediately(any())
    }

    /**
     * 커버리지 리뷰(슬라이스8)로 못박은 경로: 재조회 결과는 있지만(취소되지 않음) 다른 id로
     * 대체된 배치라면(예: 마감 후 새 배치가 열림) 넘겨받은 pending과 다른 배치이므로 발송하지 않는다.
     */
    @Test
    fun `fire는 락 안에서 재조회한 배치가 다른 id로 대체되었으면 아무것도 하지 않는다`() {
        val pending = duePending()
        val replacedByNewBatch = pending.copy(id = 99L)
        whenever(pendingRepository.findUnsentByCoupleAndActor(1L, 2L)).thenReturn(replacedByNewBatch)

        val result = service.fire(pending, now)

        assertNull(result)
        verify(pendingRepository, never()).markSent(any(), any())
        verify(notificationDispatchService, never()).dispatchImmediately(any())
    }

    /**
     * 🔴 G-5: claim 이 발송보다 먼저다. 대상 사진이 전부 삭제돼 보낼 게 없어도 배치는 이미
     * 마감(markSent)된 상태로 남는다 — 되돌리면 다음 폴링이 같은 배치를 영원히 다시 집는다.
     */
    @Test
    fun `대상 사진이 전부 삭제됐으면 발송하지 않지만 배치는 이미 마감 처리된다`() {
        val pending = duePending(photoIds = listOf(10L, 20L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L, 20L))).thenReturn(emptyList())

        val result = service.fire(pending, now)

        assertNull(result)
        verify(pendingRepository).markSent(7L, now)
        verify(notificationDispatchService, never()).dispatchImmediately(any())
    }

    @Test
    fun `부분 삭제되면 남은 사진만으로 발송한다`() {
        val pending = duePending(photoIds = listOf(10L, 20L, 30L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L, 20L, 30L)))
            .thenReturn(listOf(createPhoto(id = 10L, address = "성수동"), createPhoto(id = 30L, address = "성수동")))
        stubActorName("테스트")
        stubDispatchEcho()

        val result = service.fire(pending, now)

        assertEquals(2, assertNotNull(result).groupCount)
    }

    @Test
    fun `동일 주소 사진들은 하나로 묶여 발송된다`() {
        val pending = duePending(photoIds = listOf(10L, 20L, 30L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L, 20L, 30L)))
            .thenReturn(
                listOf(
                    createPhoto(id = 10L, address = "성동구 성수동"),
                    createPhoto(id = 20L, address = "성동구 성수동"),
                    createPhoto(id = 30L, address = "성동구 성수동"),
                ),
            )
        stubActorName("테스트")
        stubDispatchEcho()

        val result = assertNotNull(service.fire(pending, now))

        assertEquals("성동구 성수동", result.targetAddress)
        assertTrue(result.body.contains("사진 3장을 올렸어요"), "실제 본문: ${result.body}")
    }

    /**
     * 🔴 핵심(D4): 주소가 하나라도 다르면 '한 장소'라고 말할 수 없다. targetAddress 를 null 로 두어
     * 클라이언트가 특정 핀이 아니라 지도 홈으로 보내게 한다 — 아무 사진의 주소나 대표로 고르면
     * 딥링크가 엉뚱한 장소를 연다.
     */
    @Test
    fun `서로 다른 주소가 섞이면 여러 장소로 처리된다`() {
        val pending = duePending(photoIds = listOf(10L, 20L, 30L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L, 20L, 30L)))
            .thenReturn(
                listOf(
                    createPhoto(id = 10L, address = "성동구 성수동"),
                    createPhoto(id = 20L, address = "마포구 연남동"),
                    createPhoto(id = 30L, address = "성동구 성수동"),
                ),
            )
        stubActorName("테스트")
        stubDispatchEcho()

        val result = assertNotNull(service.fire(pending, now))

        assertNull(result.targetAddress)
        assertTrue(result.body.contains("새로운 추억"), "실제 본문: ${result.body}")
    }

    /**
     * D5/F13 방어선 재확인: 저장되는 순간 이미 마감돼 있어야 슬라이스3 마감 배치가 이 알림을
     * 집어 본문을 "댓글 N개"로 덮어쓰지 못한다. 팩터리 단위 테스트(계층2)와 별개로,
     * 이 서비스가 그 팩터리를 실제로 통과시키는지를 발송 경로에서 못박는다.
     */
    @Test
    fun `저장되는 알림은 groupClosedAt이 채워져 있다`() {
        val pending = duePending(photoIds = listOf(10L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L)))
            .thenReturn(listOf(createPhoto(id = 10L, address = "성수동")))
        stubActorName("테스트")
        stubDispatchEcho()

        service.fire(pending, now)

        val captor = argumentCaptor<Notification>()
        verify(notificationDispatchService).dispatchImmediately(captor.capture())
        assertNotNull(captor.firstValue.groupClosedAt)
    }

    /**
     * 대표 사진은 '배치 목록 순서상 마지막 생존자'다. findAllByIds 는 순서를 보장하지 않으므로(D6)
     * 리포지토리가 돌려준 리스트의 last() 를 그냥 쓰면 안 된다 — photoIds 순서로 다시 정렬해야 한다.
     */
    @Test
    fun `대표 사진은 목록 순서상 마지막 생존 사진이다`() {
        val pending = duePending(photoIds = listOf(10L, 20L, 30L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L, 20L, 30L)))
            .thenReturn(listOf(createPhoto(id = 30L, address = "성수동"), createPhoto(id = 10L, address = "성수동")))
        stubActorName("테스트")
        stubDispatchEcho()

        val result = assertNotNull(service.fire(pending, now))

        assertEquals(30L, result.targetPhotoId)
    }

    @Test
    fun `이름 조회가 실패하면 상대방으로 표시된다`() {
        val pending = duePending(photoIds = listOf(10L))
        stubClaim(pending)
        whenever(photoRepository.findAllByIds(listOf(10L)))
            .thenReturn(listOf(createPhoto(id = 10L, address = "성수동")))
        whenever(userRepository.findById(2L)).thenReturn(null)
        stubDispatchEcho()

        val result = assertNotNull(service.fire(pending, now))

        assertTrue(result.body.startsWith("상대방님이"), "실제 본문: ${result.body}")
    }

    /** scheduledAt == now 는 발송 대상이다(N-1 과 부호 반대, D3). */
    private fun duePending(photoIds: List<Long> = listOf(10L)): PendingUploadNotification =
        createPendingUploadNotification(
            id = 7L,
            coupleId = 1L,
            recipientUserId = 1L,
            actorUserId = 2L,
            photoIds = photoIds,
            scheduledAt = now,
        )

    /** 락 안 재조회가 통과하고 claim(markSent)이 성공하는 정상 경로. */
    private fun stubClaim(pending: PendingUploadNotification) {
        whenever(pendingRepository.findUnsentByCoupleAndActor(pending.coupleId, pending.actorUserId))
            .thenReturn(pending)
        whenever(pendingRepository.markSent(pending.id, now)).thenReturn(pending.copy(sentAt = now))
    }

    private fun stubActorName(name: String) {
        whenever(userRepository.findById(2L)).thenReturn(createUser(id = 2L, name = name))
    }

    private fun stubDispatchEcho() {
        whenever(notificationDispatchService.dispatchImmediately(any()))
            .thenAnswer { it.getArgument<Notification>(0) }
    }
}
