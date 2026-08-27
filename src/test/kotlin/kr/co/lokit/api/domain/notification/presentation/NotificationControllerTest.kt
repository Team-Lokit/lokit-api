package kr.co.lokit.api.domain.notification.presentation

import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationInboxUseCase
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.fixture.createNotification
import kr.co.lokit.api.fixture.userAuth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(NotificationController::class)
class NotificationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var compositeAuthenticationResolver: CompositeAuthenticationResolver

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @MockitoBean
    lateinit var cookieProperties: CookieProperties

    @MockitoBean
    lateinit var cookieGenerator: CookieGenerator

    @MockitoBean
    lateinit var notificationInboxUseCase: NotificationInboxUseCase

    // R24
    @Test
    fun `알림함 목록을 페이지 메타와 함께 내려준다`() {
        whenever(notificationInboxUseCase.getInbox(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                PageResult(
                    content =
                        listOf(
                            createNotification(
                                notifId = "notif-abc",
                                notificationType = NotificationType.COMMENT,
                                title = "새 댓글",
                                body = "상대방님이 댓글을 남겼어요",
                                groupCount = 3,
                                targetPhotoId = 42L,
                                targetAddress = "서울 강남구",
                                sentAt = LocalDateTime.of(2026, 1, 1, 12, 0),
                            ),
                        ),
                    page = 0,
                    size = 20,
                    totalElements = 1L,
                ),
            )

        mockMvc.perform(
            get("/notifications")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].notifId").value("notif-abc"))
            .andExpect(jsonPath("$.data.content[0].type").value("COMMENT"))
            .andExpect(jsonPath("$.data.content[0].title").value("새 댓글"))
            .andExpect(jsonPath("$.data.content[0].body").value("상대방님이 댓글을 남겼어요"))
            .andExpect(jsonPath("$.data.content[0].groupCount").value(3))
            .andExpect(jsonPath("$.data.content[0].targetPhotoId").value(42))
            .andExpect(jsonPath("$.data.content[0].targetAddress").value("서울 강남구"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20))
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.isLast").value(true))
    }

    // R24-보강 — 숫자 PK 는 응답에 실리지 않는다(0-8, 0-R-2)
    @Test
    fun `알림함 항목은 숫자 PK를 노출하지 않는다`() {
        whenever(notificationInboxUseCase.getInbox(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                PageResult(
                    content = listOf(createNotification(id = 777L, notifId = "notif-abc")),
                    page = 0,
                    size = 20,
                    totalElements = 1L,
                ),
            )

        mockMvc.perform(
            get("/notifications")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].id").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].recipientUserId").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].actorUserId").doesNotExist())
    }

    // R25 ★ — Jackson 이 isRead 를 read 로 잘라먹지 않는다
    @Test
    fun `읽음 여부가 isRead 키로 내려간다`() {
        whenever(notificationInboxUseCase.getInbox(any(), anyOrNull(), anyOrNull()))
            .thenReturn(
                PageResult(
                    content = listOf(createNotification(notifId = "notif-abc", isRead = true)),
                    page = 0,
                    size = 20,
                    totalElements = 1L,
                ),
            )

        val body =
            mockMvc.perform(
                get("/notifications")
                    .with(authentication(userAuth())),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].isRead").value(true))
                .andExpect(jsonPath("$.data.content[0].read").doesNotExist())
                .andReturn()
                .response
                .contentAsString

        assertThat(body).contains("\"isRead\"")
        assertThat(body).doesNotContain("\"read\"")
    }

    // R26
    @Test
    fun `page와 size 파라미터를 유스케이스에 그대로 전달한다`() {
        whenever(notificationInboxUseCase.getInbox(any(), anyOrNull(), anyOrNull()))
            .thenReturn(PageResult(content = emptyList(), page = 1, size = 5, totalElements = 0L))

        mockMvc.perform(
            get("/notifications")
                .param("page", "1")
                .param("size", "5")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)

        verify(notificationInboxUseCase).getInbox(1L, 1, 5)
    }

    // R27 — 컨트롤러는 기본값을 갖지 않는다(정규화는 유스케이스 한 곳에만)
    @Test
    fun `파라미터를 생략하면 널로 전달한다`() {
        whenever(notificationInboxUseCase.getInbox(any(), anyOrNull(), anyOrNull()))
            .thenReturn(PageResult(content = emptyList(), page = 0, size = 20, totalElements = 0L))

        mockMvc.perform(
            get("/notifications")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)

        verify(notificationInboxUseCase).getInbox(1L, null, null)
    }

    // R28 — Unit 반환이 ApiResponseAdvice 를 통과하지 않는다 → 진짜 빈 204
    @Test
    fun `읽음 처리는 204를 반환하고 본문이 비어 있다`() {
        mockMvc.perform(
            patch("/notifications/notif-abc/read")
                .with(authentication(userAuth()))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        verify(notificationInboxUseCase).markAsRead(1L, "notif-abc")
    }

    // R29 — csrf() 를 붙인 채로 403 이어야 권한 403 과 구분된다(F-신규-9)
    @Test
    fun `남의 알림 읽음 처리는 403이다`() {
        doThrow(BusinessException.ForbiddenException("본인의 알림만 읽음 처리할 수 있습니다."))
            .`when`(notificationInboxUseCase)
            .markAsRead(1L, "notif-others")

        mockMvc.perform(
            patch("/notifications/notif-others/read")
                .with(authentication(userAuth()))
                .with(csrf()),
        )
            .andExpect(status().isForbidden)
    }

    // R30
    @Test
    fun `없는 알림 읽음 처리는 404다`() {
        doThrow(BusinessException.ResourceNotFoundException("Notification(notifId=notif-missing)을(를) 찾을 수 없습니다"))
            .`when`(notificationInboxUseCase)
            .markAsRead(1L, "notif-missing")

        mockMvc.perform(
            patch("/notifications/notif-missing/read")
                .with(authentication(userAuth()))
                .with(csrf()),
        )
            .andExpect(status().isNotFound)
    }

    // R31
    @Test
    fun `인증되지 않은 사용자는 알림함을 볼 수 없다`() {
        mockMvc.perform(get("/notifications"))
            .andExpect(status().isUnauthorized)
    }

    // R32
    @Test
    fun `인증되지 않은 사용자는 읽음 처리할 수 없다`() {
        mockMvc.perform(
            patch("/notifications/notif-abc/read")
                .with(csrf()),
        )
            .andExpect(status().isUnauthorized)
    }
}
