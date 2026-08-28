package kr.co.lokit.api.domain.notification.application.port.`in`

import kr.co.lokit.api.common.dto.PageResult
import kr.co.lokit.api.domain.notification.domain.Notification

/**
 * 컨트롤러 전용 인바운드 포트(선례: NotificationSettingsUseCase ← NotificationSettingsController).
 * page/size 는 nullable 로 받아 여기서 한 번만 정규화한다 — 컨트롤러에 기본값을 두면 두 곳에 규칙이 생긴다.
 * 반환 타입 PageResult 는 common.dto 라 ArchUnit 규칙3(..domain..dto..)에 걸리지 않는다.
 *
 * 권한 검증은 이 서비스 안에서 한다 — 저장소 관례(@PreAuthorize)를 의도적으로 벗어난 결정이다.
 * @PreAuthorize 는 @WebMvcTest 에서 검증 불가능함이 실측으로 확인됐다(MapControllerTest 거짓 초록).
 */
interface NotificationInboxUseCase {
    /** page<0 → 0, size 는 [1, MAX_PAGE_SIZE] 로 클램프, null → 기본값. 절대 예외를 던지지 않는다. */
    fun getInbox(
        userId: Long,
        page: Int?,
        size: Int?,
    ): PageResult<Notification>

    /**
     * 멱등. 이미 읽었으면 쓰기 없이 반환한다.
     * @throws kr.co.lokit.api.common.exception.BusinessException.ResourceNotFoundException notifId 가 없을 때 (404)
     * @throws kr.co.lokit.api.common.exception.BusinessException.ForbiddenException 수신자가 userId 가 아닐 때 (403)
     */
    fun markAsRead(
        userId: Long,
        notifId: String,
    )
}
