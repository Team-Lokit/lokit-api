package kr.co.lokit.api.domain.photo.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface EmoticonJpaRepository : JpaRepository<EmoticonEntity, Long> {
    fun findAllByCommentIn(comments: List<CommentEntity>): List<EmoticonEntity>

    fun findByCommentIdAndUserIdAndEmoji(
        commentId: Long,
        userId: Long,
        emoji: String,
    ): EmoticonEntity?

    /**
     * `@SoftDelete` 로 삭제된 행이 (comment_id, user_id, emoji) 유니크 제약을 계속 점유하므로,
     * 같은 이모지를 다시 추가하기 전에 삭제된 행을 물리 삭제한다. 네이티브 쿼리라 soft-delete 필터를 우회한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            delete from emoticon
            where comment_id = :commentId
              and user_id = :userId
              and emoji = :emoji
              and is_deleted = true
        """,
        nativeQuery = true,
    )
    fun purgeDeleted(
        commentId: Long,
        userId: Long,
        emoji: String,
    ): Int

    fun existsByCommentIdAndUserIdAndEmoji(
        commentId: Long,
        userId: Long,
        emoji: String,
    ): Boolean

    @Query(
        """
        select e.id
        from Emoticon e
        where e.userId = :userId
            and e.comment.photo.album.couple.id in :coupleIds
        """,
    )
    fun findIdsByUserIdAndCoupleIds(
        userId: Long,
        coupleIds: Set<Long>,
    ): List<Long>

    @Query(
        """
        select e.id
        from Emoticon e
        where e.userId = :userId
            and e.comment.id in :commentIds
        """,
    )
    fun findIdsByUserIdAndCommentIds(
        userId: Long,
        commentIds: Set<Long>,
    ): List<Long>

    @Query(
        """
        select e.id
        from Emoticon e
        where e.userId = :userId
            and e.comment.photo.id in :photoIds
        """,
    )
    fun findIdsByUserIdAndPhotoIds(
        userId: Long,
        photoIds: Set<Long>,
    ): List<Long>
}
