package kr.co.lokit.api.domain.notification.domain

/**
 * 디바이스 플랫폼.
 * 상수 이름은 요청 JSON 값과 device_token.platform 컬럼에 문자열 그대로 실린다.
 * rename/삭제 금지. 새 플랫폼 추가 시 DevicePlatformTest 갱신 + 슬라이스3 FCM 어댑터 분기 검토.
 */
enum class DevicePlatform {
    ANDROID,
    IOS,
}
