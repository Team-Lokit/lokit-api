package kr.co.lokit.api.domain.notification.application.port

import kr.co.lokit.api.domain.notification.domain.DeviceToken

interface DeviceTokenRepositoryPort {
    /** token을 자연키로 upsert. 같은 token 존재 시 userId/platform 갱신, 없으면 신규. 멱등. */
    fun upsert(deviceToken: DeviceToken): DeviceToken

    /**
     * 물리 삭제. BaseEntity의 @SoftDelete를 의도적으로 우회한다 (OQ-5).
     * 소프트 삭제로 남으면 token 유니크 제약을 계속 점유해 재로그인 시 등록이 영구 실패한다.
     */
    fun deleteAllByUserId(userId: Long): Int

    fun findAllByUserId(userId: Long): List<DeviceToken>

    /**
     * 물리 삭제. 발송 결과 무효로 판정된 토큰 정리용. 빈 컬렉션이면 쿼리 없이 0.
     * deleteAllByUserId 와 같은 이유로 @SoftDelete 를 우회한다.
     */
    fun deleteAllByTokens(tokens: Collection<String>): Int

    /** 물리 삭제. user_id 와 token 둘 다 일치하는 행만 지운다 (다른 사용자 소유 토큰은 건드리지 않음). */
    fun deleteByUserIdAndToken(userId: Long, token: String): Int
}
