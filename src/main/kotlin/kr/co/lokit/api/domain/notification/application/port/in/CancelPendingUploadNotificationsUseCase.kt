package kr.co.lokit.api.domain.notification.application.port.`in`

/** couple 도메인 연결해제 훅이 호출하는 인바운드 포트 (선례: AuthService→DeleteDeviceTokensUseCase). 스펙 3.7 */
interface CancelPendingUploadNotificationsUseCase {
    fun cancelByCoupleId(coupleId: Long): Int
}
