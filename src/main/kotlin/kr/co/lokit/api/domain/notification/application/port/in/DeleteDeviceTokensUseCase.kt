package kr.co.lokit.api.domain.notification.application.port.`in`

/** user 도메인의 로그아웃 훅이 호출하는 인바운드 포트 (선례: KakaoLoginService→couple.CreateCoupleUseCase) */
interface DeleteDeviceTokensUseCase {
    fun deleteAllByUserId(userId: Long)
}
