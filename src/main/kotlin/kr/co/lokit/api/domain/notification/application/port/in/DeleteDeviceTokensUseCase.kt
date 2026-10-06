package kr.co.lokit.api.domain.notification.application.port.`in`

/** user 도메인의 로그아웃 훅이 호출하는 인바운드 포트 (선례: KakaoLoginService→couple.CreateCoupleUseCase) */
interface DeleteDeviceTokensUseCase {
    /** 사용자의 모든 디바이스 토큰을 물리 삭제한다. 로그아웃 본문에 deviceToken 이 없을 때 사용. */
    fun deleteAllByUserId(userId: Long)

    /**
     * 사용자 소유의 해당 토큰 1건만 물리 삭제한다(기기 단위 로그아웃).
     * 다른 사용자 소유이거나 없는 토큰이면 아무것도 지우지 않는다.
     */
    fun deleteByUserIdAndToken(userId: Long, token: String)
}
