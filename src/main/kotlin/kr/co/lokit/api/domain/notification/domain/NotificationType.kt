package kr.co.lokit.api.domain.notification.domain

/**
 * 푸시 알림 종류.
 *
 * 확장 가이드:
 * - 상수 이름은 app_event_log.notif_type 컬럼과 FCM data payload 에 문자열 그대로 실린다.
 *   기존 상수의 rename/삭제는 금지.
 * - 새 타입(REMIND 등) 추가 시: (1) 여기에 상수 추가 (2) NotificationTypeTest 갱신
 *   (3) 알림 설정 API(슬라이스 5) 토글 키 추가 검토.
 * - domain 레이어이므로 application/infrastructure/presentation 참조 금지
 *   (HexagonalArchitectureTest 규칙 1).
 */
enum class NotificationType {
    COMMENT,
    REACTION,
    UPLOAD,
}
