package kr.co.lokit.api.domain.notification.infrastructure.fcm

/** 인터페이스 분리 이유: FcmPushSenderAdapter 단위 테스트가 google-auth-library를 안 건드리게. */
interface FcmAccessTokenProvider {
    fun accessToken(): String
}
