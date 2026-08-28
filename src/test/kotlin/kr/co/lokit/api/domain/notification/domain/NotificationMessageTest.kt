package kr.co.lokit.api.domain.notification.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 계약 2-2. 순수 함수라 슬라이스 없음.
 * 제목은 "최초 이벤트 종류로 고정", 본문은 "그룹 개수가 2 이상이면 타입별 통합 카운트"가 계약이다.
 * (버그픽스: 과거엔 그룹 분기가 타입 상관없이 "댓글"로 고정돼 있었다 — 화면 기획 대조로 발견,
 * `그룹 개수가 2 이상이면 반응 알림은 반응 문구를 쓴다` 테스트가 이 회귀를 막는다.)
 */
class NotificationMessageTest {

    @Test
    fun `댓글 알림 제목은 새 댓글이다`() {
        assertEquals("새 댓글", NotificationMessage.title(NotificationType.COMMENT))
    }

    @Test
    fun `반응 알림 제목은 새 반응이다`() {
        assertEquals("새 반응", NotificationMessage.title(NotificationType.REACTION))
    }

    @Test
    fun `그룹 개수가 1이면 타입별 단일 문구를 쓴다`() {
        assertEquals(
            "지민님이 댓글을 남겼어요",
            NotificationMessage.body("지민", NotificationType.COMMENT, 1),
        )
        assertEquals(
            "지민님이 반응을 남겼어요",
            NotificationMessage.body("지민", NotificationType.REACTION, 1),
        )
    }

    @Test
    fun `그룹 개수가 2 이상이면 통합 카운트 문구가 된다`() {
        assertEquals(
            "지민님이 댓글 3개를 남겼어요",
            NotificationMessage.body("지민", NotificationType.COMMENT, 3),
        )
    }

    @Test
    fun `그룹 개수가 2 이상이면 반응 알림은 반응 문구를 쓴다`() {
        assertEquals(
            "지민님이 반응 2개를 남겼어요",
            NotificationMessage.body("지민", NotificationType.REACTION, 2),
        )
    }

    /**
     * 버그2 픽스: 단건 반응 본문에 실제로 남긴 이모지를 담는다.
     * 그룹으로 쌓이면(여러 이모지가 섞일 수 있어) 이모지를 보여주지 않는다 — 화면 기획도
     * 그룹 반응 줄엔 이모지 없이 "반응 N개"만 보여준다(위 테스트가 그 경로를 이미 못박는다).
     */
    @Test
    fun `단건 반응 알림은 이모지를 본문에 담는다`() {
        assertEquals(
            "지민님이 ❤️ 반응을 남겼어요",
            NotificationMessage.body("지민", NotificationType.REACTION, 1, emoji = "❤️"),
        )
    }

    @Test
    fun `이모지가 없으면 기존처럼 일반 반응 문구를 쓴다`() {
        assertEquals(
            "지민님이 반응을 남겼어요",
            NotificationMessage.body("지민", NotificationType.REACTION, 1, emoji = null),
        )
    }

    @Test
    fun `댓글 알림은 이모지 인자를 받아도 무시한다`() {
        assertEquals(
            "지민님이 댓글을 남겼어요",
            NotificationMessage.body("지민", NotificationType.COMMENT, 1, emoji = "❤️"),
        )
    }

    @Test
    fun `행위자 이름이 비어 있으면 기본 이름을 쓴다`() {
        assertEquals(
            "상대방님이 댓글을 남겼어요",
            NotificationMessage.body("", NotificationType.COMMENT, 1),
        )
        assertEquals("상대방", NotificationMessage.DEFAULT_ACTOR_NAME)
    }

    /**
     * 계약 2-5 / D7. uploadBody 는 기존 body() 와 별개 함수다 — UPLOAD 는 사진 장수·주소
     * 조합이라는 자기만의 분기 축이 있어(address 유무 × 사진 장수) body() 의 COMMENT/REACTION
     * 전용 그룹 카운트 분기를 재사용하지 않는다.
     */
    @Test
    fun `사진 한 장을 한 장소에 올리면 새로운 추억 문구를 쓴다`() {
        assertEquals(
            "지민님이 성수동에 새로운 추억을 남겼어요",
            NotificationMessage.uploadBody("지민", 1, "성수동"),
        )
    }

    @Test
    fun `여러 장을 같은 장소에 올리면 주소와 장수를 함께 쓴다`() {
        assertEquals(
            "지민님이 성수동에 사진 5장을 올렸어요",
            NotificationMessage.uploadBody("지민", 5, "성수동"),
        )
    }

    @Test
    fun `공통 주소가 없으면 장소를 빼고 장수만 쓴다`() {
        assertEquals(
            "지민님이 새로운 추억 8장을 남겼어요",
            NotificationMessage.uploadBody("지민", 8, null),
        )
    }

    @Test
    fun `업로드 본문도 행위자 이름이 비어 있으면 기본 이름을 쓴다`() {
        assertEquals(
            "상대방님이 성수동에 새로운 추억을 남겼어요",
            NotificationMessage.uploadBody("", 1, "성수동"),
        )
    }

    /**
     * 주소는 리버스 지오코딩 결과라 길이 상한이 없다.
     * 절단하지 않으면 FCM 본문 제약(MAX_BODY_LENGTH)을 넘긴다.
     */
    @Test
    fun `주소가 아주 길면 본문은 최대 길이로 잘린다`() {
        val longAddress = "가".repeat(300)

        val body = NotificationMessage.uploadBody("지민", 1, longAddress)

        assertEquals(NotificationMessage.MAX_BODY_LENGTH, body.length)
        assertTrue(body.startsWith("지민님이 가가가"))
    }
}
