package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import kr.co.lokit.api.common.entity.BaseEntity
import kr.co.lokit.api.domain.notification.domain.NotificationSettings

/**
 * 유저당 1행. 종류별 오버라이드는 자식 테이블이 아니라 구분자 문자열 컬럼이다
 * (선례: PendingUploadNotificationEntity.photoIds — JSON/Converter 선례 0건).
 * 자식 테이블 안을 버린 이유: 자식 행이 "명시적 true"도 저장하게 되어
 * '저장 안 됨 = 기본 ON' 규칙이 데이터에 의존해 무너진다.
 *
 * user_id에 유니크 제약을 둔다 — PendingUploadNotificationEntity(F15)와 반대 결정이며 근거는:
 * (a) 이 테이블에는 삭제 경로가 코드상 존재하지 않는다(포트에 delete 없음) → @SoftDelete 행이
 *     유니크 인덱스를 점유하는 슬라이스2 함정이 발생할 수 없다
 * (b) 사용자는 하드삭제되지 않고 익명화된다(DeviceTokenEntity.kt:16 선례) → user_id 재사용 없음.
 *     탈퇴 시에도 이 행을 지우지 않는다 — 재가입 시 KakaoLoginService.kt:82/AppleLoginService.kt:75가
 *     같은 user id를 되살리므로, 행을 남겨둬야 재가입 후 설정이 복원된다(명시적 결정, §6-6)
 * (c) 중복 행이 생기면 "어느 설정이 진짜냐"가 비결정적이 되어 알림이 무작위로 켜졌다 꺼졌다 한다 —
 *     이 도메인에서 가장 나쁜 실패 양상이다
 * (d) 동시 PATCH 경합의 유니크 위반/낙관적 락 예외는 잡지 않고 전파한다 → ErrorControllerAdvice가
 *     409, 클라이언트 재시도로 자가치유 (선례: JpaDeviceTokenRepository.kt:12-14, OQ-1 / Q7)
 */
@Entity(name = "NotificationSetting")
@Table(
    name = "notification_setting",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])],
)
class NotificationSettingEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "master_enabled", nullable = false)
    var masterEnabled: Boolean,
    @Column(
        name = "disabled_types",
        nullable = false,
        length = NotificationSettings.DISABLED_TYPES_COLUMN_LENGTH,
    )
    var disabledTypes: String,
) : BaseEntity()
