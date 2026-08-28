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
}
