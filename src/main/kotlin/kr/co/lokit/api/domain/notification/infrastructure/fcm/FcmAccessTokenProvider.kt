package kr.co.lokit.api.domain.notification.infrastructure.fcm

/** 인터페이스 분리 이유: FcmPushSenderAdapter 단위 테스트가 google-auth-library를 안 건드리게. */
interface FcmAccessTokenProvider {
    fun accessToken(): String

    /** FCM 이 401 을 돌려줬을 때 호출한다. 다음 accessToken() 이 캐시를 무시하고 새로 발급받게 한다. */
    fun invalidate() {}
}
