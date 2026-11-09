package kr.co.lokit.api.domain.user.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.user.application.port.UserHardDeletePort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * BaseEntity 의 @SoftDelete 때문에 JPA delete 는 is_deleted 만 바꾸고 조회에서도 삭제 행이 빠진다.
 * 소프트 삭제된 행까지 물리적으로 지우기 위해 네이티브 SQL 을 쓰며, 문장 순서는 FK 의존 순서다.
 */
@Repository
class JpaUserHardDeleteAdapter(
    private val entityManager: EntityManager,
) : UserHardDeletePort {
    @Transactional(readOnly = true)
    override fun findUserIdsByEmail(email: String): List<Long> =
        entityManager
            .createNativeQuery("SELECT id FROM users WHERE email = :email")
            .setParameter("email", email)
            .resultList
            .map { (it as Number).toLong() }

    @Transactional
    override fun hardDelete(userIds: List<Long>): List<String> {
        if (userIds.isEmpty()) return emptyList()

        val coupleIds = findPurgeTargetCoupleIds(userIds)
        val photoUrls = if (coupleIds.isEmpty()) emptyList() else findPhotoUrls(coupleIds)

        if (coupleIds.isNotEmpty()) {
            COUPLE_DELETE_STATEMENTS.forEach { execute(it, "coupleIds", coupleIds) }
        }
        USER_DELETE_STATEMENTS.forEach { execute(it, "userIds", userIds) }

        return photoUrls
    }

    private fun findPurgeTargetCoupleIds(userIds: List<Long>): List<Long> =
        entityManager
            .createNativeQuery(
                """
                SELECT DISTINCT cu.couple_id
                FROM couple_user cu
                WHERE cu.user_id IN (:userIds)
                  AND (
                    cu.is_deleted = false
                    OR NOT EXISTS (
                        SELECT 1 FROM couple_user other
                        WHERE other.couple_id = cu.couple_id
                          AND other.user_id NOT IN (:userIds)
                          AND other.is_deleted = false
                    )
                  )
                """.trimIndent(),
            ).setParameter("userIds", userIds)
            .resultList
            .map { (it as Number).toLong() }

    private fun findPhotoUrls(coupleIds: List<Long>): List<String> =
        entityManager
            .createNativeQuery("SELECT p.url FROM photo p WHERE p.album_id IN ($ALBUM_IDS_IN_COUPLES)")
            .setParameter("coupleIds", coupleIds)
            .resultList
            .map { it as String }

    private fun execute(
        sql: String,
        parameter: String,
        ids: List<Long>,
    ) {
        entityManager.createNativeQuery(sql).setParameter(parameter, ids).executeUpdate()
    }

    private companion object {
        const val ALBUM_IDS_IN_COUPLES = "SELECT a.id FROM album a WHERE a.couple_id IN (:coupleIds)"
        const val PHOTO_IDS_IN_COUPLES = "SELECT p.id FROM photo p WHERE p.album_id IN ($ALBUM_IDS_IN_COUPLES)"
        const val COMMENT_IDS_IN_COUPLES = "SELECT c.id FROM comment c WHERE c.photo_id IN ($PHOTO_IDS_IN_COUPLES)"

        val COUPLE_DELETE_STATEMENTS =
            listOf(
                "DELETE FROM emoticon WHERE comment_id IN ($COMMENT_IDS_IN_COUPLES)",
                "DELETE FROM comment WHERE photo_id IN ($PHOTO_IDS_IN_COUPLES)",
                "DELETE FROM notification WHERE target_photo_id IN ($PHOTO_IDS_IN_COUPLES)",
                "DELETE FROM pending_upload_notification WHERE couple_id IN (:coupleIds)",
                """
                DELETE FROM album_bounds
                WHERE (id_type = 'ALBUM' AND standard_id IN ($ALBUM_IDS_IN_COUPLES))
                   OR (id_type = 'COUPLE' AND standard_id IN (:coupleIds))
                """.trimIndent(),
                "DELETE FROM photo WHERE album_id IN ($ALBUM_IDS_IN_COUPLES)",
                "DELETE FROM album WHERE couple_id IN (:coupleIds)",
                "DELETE FROM couple_user WHERE couple_id IN (:coupleIds)",
                "DELETE FROM couple WHERE id IN (:coupleIds)",
            )

        val USER_DELETE_STATEMENTS =
            listOf(
                "DELETE FROM refresh_token WHERE user_id IN (:userIds)",
                "DELETE FROM device_token WHERE user_id IN (:userIds)",
                "DELETE FROM notification_setting WHERE user_id IN (:userIds)",
                "DELETE FROM app_event_log WHERE user_id IN (:userIds)",
                "DELETE FROM invite_code WHERE created_by IN (:userIds) OR used_by IN (:userIds)",
                "DELETE FROM notification WHERE recipient_user_id IN (:userIds) OR actor_user_id IN (:userIds)",
                "DELETE FROM pending_upload_notification WHERE recipient_user_id IN (:userIds) OR actor_user_id IN (:userIds)",
                "DELETE FROM couple_user WHERE user_id IN (:userIds)",
                "DELETE FROM users WHERE id IN (:userIds)",
            )
    }
}
