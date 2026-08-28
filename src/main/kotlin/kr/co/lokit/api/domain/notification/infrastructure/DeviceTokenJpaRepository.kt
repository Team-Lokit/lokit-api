package kr.co.lokit.api.domain.notification.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenEntity, Long> {
    fun findByToken(token: String): DeviceTokenEntity?

    fun findAllByUserId(userId: Long): List<DeviceTokenEntity>

    /**
     * @SoftDelete 우회 물리 삭제. HQL DELETE는 Hibernate가 소프트삭제 UPDATE로 재작성하므로
     * 네이티브 SQL 사용 (선례: UserJpaRepository.restoreDeletedByEmail).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from device_token where user_id = :userId", nativeQuery = true)
    fun hardDeleteAllByUserId(userId: Long): Int
}
