package kr.co.lokit.api.domain.notification.application

import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.entityNotFound
import kr.co.lokit.api.domain.notification.application.port.NotificationRepositoryPort
import kr.co.lokit.api.domain.notification.application.port.`in`.NotificationInboxUseCase
import kr.co.lokit.api.domain.notification.domain.Notification
import org.springframework.stereotype.Service

/**
 * 클래스에 @Transactional 을 붙이지 않는다 — 트랜잭션은 어댑터 메서드가 소유한다
 * (NotificationDispatchService 와 동일한 코드베이스 규약).
 */
@Service
class NotificationInboxService(
    private val notificationRepository: NotificationRepositoryPort,
) : NotificationInboxUseCase {
    override fun getInbox(
        userId: Long,
        page: Int?,
        size: Int?,
    ): PageResult<Notification> {
        val normalizedPage = (page ?: FIRST_PAGE).coerceAtLeast(FIRST_PAGE)
        val normalizedSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        return PageResult(
            content = notificationRepository.findInboxPage(userId, normalizedPage, normalizedSize),
            page = normalizedPage,
            size = normalizedSize,
            totalElements = notificationRepository.countInbox(userId),
        )
    }

    /** 소유자 확인 → 멱등 단락 → 위임. 순서를 바꾸면 안 된다(권한 검증이 쓰기보다 먼저). */
    override fun markAsRead(
        userId: Long,
        notifId: String,
    ) {
        val notification =
            notificationRepository.findByNotifId(notifId)
                ?: throw entityNotFound<Notification>("notifId", notifId)
        if (notification.recipientUserId != userId) {
            throw BusinessException.ForbiddenException("다른 사용자의 알림은 읽음 처리할 수 없습니다")
        }
        if (notification.isRead) return
        notificationRepository.markAsRead(notification.id)
    }

    companion object {
        const val FIRST_PAGE: Int = 0
        const val MIN_PAGE_SIZE: Int = 1
        const val DEFAULT_PAGE_SIZE: Int = 20
        const val MAX_PAGE_SIZE: Int = 50
    }
}
