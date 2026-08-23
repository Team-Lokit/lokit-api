package kr.co.lokit.api.domain.photo.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CommentJpaRepository : JpaRepository<CommentEntity, Long> {
    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.user WHERE c.photo.id = :photoId ORDER BY c.createdAt ASC, c.id ASC")
    fun findAllByPhotoId(photoId: Long): List<CommentEntity>

    /**
     * Hibernate `@SoftDelete` 필터는 JPQL/Criteria 쿼리에만 적용되고 네이티브 쿼리는 우회하므로,
     * 삭제된 댓글도 조회/복구하려면 아래처럼 네이티브 쿼리를 사용해야 한다.
     */
    @Query(value = "select user_id from comment where id = :id", nativeQuery = true)
    fun findOwnerUserIdIncludingDeleted(id: Long): Long?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            update comment
            set is_deleted = false,
                updated_at = now()
            where id = :id
              and is_deleted = true
        """,
        nativeQuery = true,
    )
    fun restoreDeleted(id: Long): Int

    @Query(
        """
        select c.id
        from Comment c
        where c.user.id = :userId
            and c.photo.album.couple.id in :coupleIds
        """,
    )
    fun findIdsByUserIdAndCoupleIds(
        userId: Long,
        coupleIds: Set<Long>,
    ): List<Long>

    @Query(
        """
        select c.id
        from Comment c
        where c.user.id = :userId
            and c.photo.id in :photoIds
        """,
    )
    fun findIdsByUserIdAndPhotoIds(
        userId: Long,
        photoIds: Set<Long>,
    ): List<Long>
}
