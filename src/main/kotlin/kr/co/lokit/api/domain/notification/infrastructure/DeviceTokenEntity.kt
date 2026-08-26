package kr.co.lokit.api.domain.notification.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import kr.co.lokit.api.common.entity.BaseEntity
import kr.co.lokit.api.domain.notification.domain.DevicePlatform

/**
 * user는 FK(@ManyToOne)가 아니라 raw Long이다 (OQ-8):
 * (1) 등록마다 UserEntity SELECT를 없앤다 (2) notification.infrastructure→user.infrastructure 결합 방지
 * (3) 사용자는 하드삭제되지 않아(탈퇴=익명화) FK 무결성 요구가 없다.
 * @Table(name) 명시 이유: deleteAllByUserId가 네이티브 SQL로 이 테이블을 직접 지목한다.
 */
@Entity(name = "DeviceToken")
@Table(
    name = "device_token",
    uniqueConstraints = [UniqueConstraint(columnNames = ["token"])],
    indexes = [Index(columnList = "user_id")],
)
class DeviceTokenEntity(
    @Column(name = "token", nullable = false, length = 512)
    val token: String,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 16)
    var platform: DevicePlatform,
) : BaseEntity()
