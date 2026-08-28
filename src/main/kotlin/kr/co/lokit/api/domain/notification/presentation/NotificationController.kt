package kr.co.lokit.api.domain.notification.presentation

import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationInboxUseCase
import kr.co.lokit.api.domain.notification.dto.NotificationResponse
import kr.co.lokit.api.domain.notification.presentation.mapping.toResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("notifications")
class NotificationController(
    private val notificationInboxUseCase: NotificationInboxUseCase,
) : NotificationApi {
    /** @RequestParam 에 defaultValue 를 두지 않는다 — 기본값 규칙은 유스케이스 한 곳에만 있다. */
    @GetMapping
    override fun getNotifications(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): PageResult<NotificationResponse> = notificationInboxUseCase.getInbox(userId, page, size).map { it.toResponse() }

    /** 반환 Unit 은 ApiResponseAdvice.EXCLUDED_TYPES 라 래핑되지 않는다 → 진짜 빈 204. */
    @PatchMapping("{notifId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun markNotificationAsRead(
        @CurrentUserId userId: Long,
        @PathVariable notifId: String,
    ) = notificationInboxUseCase.markAsRead(userId, notifId)
}
