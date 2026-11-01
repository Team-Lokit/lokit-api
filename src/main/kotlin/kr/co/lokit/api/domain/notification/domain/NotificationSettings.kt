package kr.co.lokit.api.domain.notification.domain

/**
 * 사용자별 알림 설정. 유저당 1개.
 *
 * 핵심 불변식 두 가지를 "구조"로 강제한다 — 서비스 로직의 성실함에 의존하지 않는다:
 *
 * (1) "저장된 값이 없으면 기본 ON": 저장 표현이 `disabledTypes`(= 명시적으로 끈 것만)이므로
 *     새 NotificationType 이 추가되면 자동으로 켜진 것으로 읽힌다. 마이그레이션·백필 불필요.
 *     Map<Type, Boolean> 을 쓰면 "true 로 저장됨"과 "저장 안 됨"의 이중 표현이 생겨
 *     기본값 규칙이 데이터에 의존하게 된다 — 그래서 Set 이다.
 *
 * (2) "마스터 OFF 는 비활성화이지 삭제가 아니다": masterEnabled 와 disabledTypes 가 서로
 *     독립 필드이고 update() 가 서로를 건드리지 않는다. 마스터를 껐다 켜면 종류별 커스터마이즈가
 *     자동으로 복원된다 — 복원 로직이 따로 없다는 것이 요점이다.
 *
 * domain 레이어이므로 application/infrastructure/presentation 참조 금지
 * (HexagonalArchitectureTest 규칙 1).
 */
data class NotificationSettings(
    val userId: Long,
    val masterEnabled: Boolean = DEFAULT_MASTER_ENABLED,
    val disabledTypes: Set<NotificationType> = emptySet(),
) {
    /** 종류별 스위치 단독 값. 마스터를 보지 않는다. */
    fun isTypeEnabled(type: NotificationType): Boolean = type !in disabledTypes

    /** 유효 발송 여부. 게이트가 부르는 유일한 판정 함수(D-1). */
    fun isPushEnabledFor(type: NotificationType): Boolean = masterEnabled && isTypeEnabled(type)

    /**
     * 서버가 아는 모든 종류의 스위치를 명시적으로 만든다.
     * NotificationType.entries 를 순회하므로 enum 상수가 늘어나면 응답도 자동으로 늘어난다.
     */
    fun typeToggles(): Map<NotificationType, Boolean> = NotificationType.entries.associateWith { isTypeEnabled(it) }

    /**
     * 부분 업데이트. null/빈 맵은 "변경 없음"이다.
     * masterEnabled 를 바꿔도 disabledTypes 는 손대지 않는다(요구사항: 복원).
     */
    fun update(
        masterEnabled: Boolean?,
        typeToggles: Map<NotificationType, Boolean>,
    ): NotificationSettings =
        copy(
            masterEnabled = masterEnabled ?: this.masterEnabled,
            disabledTypes =
                typeToggles.entries.fold(disabledTypes) { acc, (type, enabled) ->
                    if (enabled) acc - type else acc + type
                },
        )

    companion object {
        const val DEFAULT_MASTER_ENABLED: Boolean = true
        const val DEFAULT_TYPE_ENABLED: Boolean = true
        const val DISABLED_TYPES_COLUMN_LENGTH: Int = 255
        private const val DELIMITER: String = ","

        /**
         * 저장된 행이 없는 유저의 설정. '행 없음'의 유일한 표현이다.
         * 컨트롤러용 유스케이스(§2-4)와 발송 게이트(§2-5) 둘 다 이 함수 하나만 호출한다 —
         * 기본값 데이터(마스터 true, 전종류 true) 자체는 여기 한 곳에만 있다(0-3절).
         */
        fun defaultsFor(userId: Long): NotificationSettings = NotificationSettings(userId = userId)

        /** 선례: PendingUploadNotification.encodePhotoIds (구분자 문자열, JSON/Converter 선례 0건). */
        fun encodeDisabledTypes(types: Set<NotificationType>): String = types.map { it.name }.sorted().joinToString(DELIMITER)

        /**
         * 알 수 없는 상수 이름은 조용히 무시한다.
         * NotificationType 의 rename/삭제는 금지돼 있지만, 그 규칙이 깨졌을 때
         * 전체 설정 조회가 예외로 죽는 것보다 그 종류가 기본 ON 으로 읽히는 편이 안전하다.
         */
        fun decodeDisabledTypes(raw: String): Set<NotificationType> =
            raw
                .split(DELIMITER)
                .filter { it.isNotBlank() }
                .mapNotNull { name -> NotificationType.entries.firstOrNull { it.name == name.trim() } }
                .toSet()
    }
}
