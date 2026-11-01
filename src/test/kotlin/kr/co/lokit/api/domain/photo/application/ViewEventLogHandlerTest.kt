package kr.co.lokit.api.domain.photo.application

import kr.co.lokit.api.common.analytics.application.port.AppEventLogPort
import kr.co.lokit.api.domain.photo.domain.CommentListViewedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoViewedEvent
import kr.co.lokit.api.domain.photo.domain.PhotoViewerRole
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.nullableArgumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * R9/R10/R17 못박기 (계약 §3.3, §3.4).
 *
 * 이 핸들러는 **어떤 조회도 하지 않는다** — 필요한 값은 전부 이벤트가 들고 온다.
 * 협력자가 `AppEventLogPort` 하나뿐이므로 슬라이스 없이 순수 Mockito 단위 테스트로 간다
 * (`AlbumBoundsEventHandlerTest` 와 같은 형태). `@Async @TransactionalEventListener` 의
 * 실제 발화는 이 테스트가 검증하지 않는다(계약 Q4) — 핸들러 메서드를 직접 호출한다.
 *
 * `eventName` 은 상수 참조가 아니라 문자열 리터럴로 못박는다. `photo_view`/`comment_view` 는
 * 분석 파이프라인이 읽는 **와이어 값**이라, 상수를 참조하면 상수 이름만 바꿔도 통과하는
 * 동어반복 테스트가 된다.
 */
@ExtendWith(MockitoExtension::class)
class ViewEventLogHandlerTest {
    @Mock
    lateinit var appEventLogPort: AppEventLogPort

    @InjectMocks
    lateinit var viewEventLogHandler: ViewEventLogHandler

    @Test
    fun `PhotoViewedEvent를 받으면 photo_view 를 기록한다`() {
        val event =
            PhotoViewedEvent(
                photoId = 10L,
                viewerUserId = 2L,
                photoOwnerId = 1L,
                viewerRole = PhotoViewerRole.PARTNER,
            )

        viewEventLogHandler.handlePhotoViewed(event)

        val paramsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(appEventLogPort).record(
            eventName = eq("photo_view"),
            userId = eq(2L),
            notifId = eq(null),
            notifType = eq(null),
            params = paramsCaptor.capture(),
        )
        val params = paramsCaptor.firstValue
        assertEquals(10L, params["photo_id"])
        assertEquals(1L, params["photo_owner_id"])
        assertEquals("PARTNER", params["viewer_role"])
        // 키를 통째로 못박는다: 스키마에 없는 키가 늘면 분석 파이프라인이 조용히 어긋난다(계약 §3.3).
        assertEquals(setOf("photo_id", "photo_owner_id", "viewer_role"), params.keys)
        // 핸들러가 사진/커플을 재조회하는 등 추가 협력을 하면 여기서 잡힌다(T-A6).
        verifyNoMoreInteractions(appEventLogPort)
    }

    @Test
    fun `photo_view 기록 시 notifId 와 notifType 은 비운다`() {
        val event =
            PhotoViewedEvent(
                photoId = 10L,
                viewerUserId = 2L,
                photoOwnerId = 1L,
                viewerRole = PhotoViewerRole.OWNER,
            )

        viewEventLogHandler.handlePhotoViewed(event)

        // notifId/notifType 은 알림 전용 필드다(계약 §7-2). 조회 이벤트가 여기에 값을 흘리면
        // 알림 지표의 분모가 오염된다 — 그래서 eq(null) 이 아니라 캡처해서 직접 단언한다.
        val notifIdCaptor = nullableArgumentCaptor<String>()
        val notifTypeCaptor = nullableArgumentCaptor<String>()
        val paramsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(appEventLogPort).record(
            eventName = eq("photo_view"),
            userId = eq(2L),
            notifId = notifIdCaptor.capture(),
            notifType = notifTypeCaptor.capture(),
            params = paramsCaptor.capture(),
        )
        assertNull(notifIdCaptor.firstValue)
        assertNull(notifTypeCaptor.firstValue)
        verifyNoMoreInteractions(appEventLogPort)
    }

    @Test
    fun `CommentListViewedEvent를 받으면 comment_view 를 기록한다`() {
        val event =
            CommentListViewedEvent(
                photoId = 10L,
                viewerUserId = 99L,
                photoOwnerId = 1L,
                viewerRole = PhotoViewerRole.OTHER,
                commentCount = 3,
            )

        viewEventLogHandler.handleCommentListViewed(event)

        val paramsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(appEventLogPort).record(
            eventName = eq("comment_view"),
            userId = eq(99L),
            notifId = eq(null),
            notifType = eq(null),
            params = paramsCaptor.capture(),
        )
        val params = paramsCaptor.firstValue
        assertEquals(10L, params["photo_id"])
        assertEquals(1L, params["photo_owner_id"])
        // 댓글 작성자가 아니라 **사진 업로더** 기준 역할이다(계약 0-R-3).
        assertEquals("OTHER", params["viewer_role"])
        assertEquals(3, params["comment_count"])
        assertEquals(
            setOf("photo_id", "photo_owner_id", "viewer_role", "comment_count"),
            params.keys,
        )
        verifyNoMoreInteractions(appEventLogPort)
    }
}
