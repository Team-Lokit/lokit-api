package kr.co.lokit.api.domain.notification.application.port

import kr.co.lokit.api.domain.notification.domain.NotificationSettings

/**
 * 포트는 '기본값 ON' 정책을 모른다. 행이 없으면 null 을 그대로 돌려주고,
 * 기본값 합성은 호출부(NotificationSettingsService, NotificationDispatchService 게이트)가
 * `NotificationSettings.defaultsFor(userId)` 를 통해 각자 한다(계약 0-3절).
 * 삭제 메서드는 두지 않는다 — 설정은 삭제가 아니라 갱신이다(요구사항, 계약 §6-4).
 *
 * 반환 타입이 nullable 인 것은 우연이 아니라 계약이다: NotificationDispatchServiceTest 의
 * 기존 11개 테스트가 이 포트를 스텁하지 않으면 Mockito 기본값(null)이 그대로 반환되고,
 * `?: defaultsFor(...)` 가 이를 "전부 ON"으로 흡수해 회귀 없이 통과한다(계약 0-3, §6-2).
 */
interface NotificationSettingsRepositoryPort {
    fun findByUserId(userId: Long): NotificationSettings?

    /** userId 를 자연키로 upsert. 존재하면 갱신, 없으면 신규. 멱등. */
    fun save(settings: NotificationSettings): NotificationSettings
}

/**
 * 세 호출부(NotificationSettingsService.getSettings / .updateSettings /
 * NotificationDispatchService 게이트)가 각자 반복하던 `findByUserId(id) ?: defaultsFor(id)` 를 모은다.
 *
 * 인터페이스 default 메서드가 아니라 확장함수인 이유: 이 저장소의 포트는 예외 없이 순수 추상이고,
 * 기본값 합성은 포트의 책임이 아니라 호출부의 책임이라는 계약 0-3절을 흐리지 않기 위해서다.
 * 어댑터가 구현할 표면은 여전히 findByUserId / save 둘뿐이고, 목킹 대상도 findByUserId 하나뿐이다.
 */
fun NotificationSettingsRepositoryPort.findOrDefault(userId: Long): NotificationSettings =
    findByUserId(userId) ?: NotificationSettings.defaultsFor(userId)
