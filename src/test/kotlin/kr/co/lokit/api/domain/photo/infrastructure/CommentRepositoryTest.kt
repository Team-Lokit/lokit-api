package kr.co.lokit.api.domain.photo.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleEntity
import kr.co.lokit.api.domain.couple.infrastructure.CoupleJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleUserEntity
import kr.co.lokit.api.domain.user.infrastructure.UserEntity
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.fixture.createAlbumEntity
import kr.co.lokit.api.fixture.createCoupleEntity
import kr.co.lokit.api.fixture.createPhotoEntity
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
@Import(JpaCommentRepository::class)
class CommentRepositoryTest {
    @Autowired
    lateinit var commentJpaRepository: CommentJpaRepository

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    lateinit var coupleJpaRepository: CoupleJpaRepository

    @Autowired
    lateinit var photoJpaRepository: PhotoJpaRepository

    @Autowired
    lateinit var albumJpaRepository: AlbumJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    lateinit var couple: CoupleEntity
    lateinit var owner: UserEntity
    lateinit var author: UserEntity
    lateinit var photo: PhotoEntity

    @BeforeEach
    fun setUp() {
        owner = userJpaRepository.save(createUserEntity(email = "owner@test.com"))
        author = userJpaRepository.save(createUserEntity(email = "author@test.com"))
        couple = createCoupleEntity()
        couple.addUser(CoupleUserEntity(couple = couple, user = owner))
        couple.addUser(CoupleUserEntity(couple = couple, user = author))
        couple = coupleJpaRepository.save(couple)
        val album = albumJpaRepository.save(createAlbumEntity(couple = couple, createdBy = owner))
        photo = photoJpaRepository.save(createPhotoEntity(album = album, uploadedBy = owner))
    }

    @Test
    fun `댓글 작성자가 탈퇴(소프트 삭제)해도 댓글은 목록에서 사라지지 않는다`() {
        val photoId = photo.nonNullId()
        val authorId = author.nonNullId()
        commentJpaRepository.save(
            CommentEntity(photo = photo, user = author, content = "댓글", commentedAt = LocalDate.now()),
        )
        commentJpaRepository.flush()
        entityManager.clear()

        val managedAuthor = userJpaRepository.findById(authorId).get()
        userJpaRepository.delete(managedAuthor)
        userJpaRepository.flush()
        entityManager.clear()

        val result = commentJpaRepository.findAllByPhotoId(photoId)

        assertEquals(1, result.size)
        assertNull(result[0].user)
    }

    @Test
    fun `탈퇴하지 않은 작성자의 댓글은 정상적으로 user가 채워진다`() {
        commentJpaRepository.save(
            CommentEntity(photo = photo, user = author, content = "댓글", commentedAt = LocalDate.now()),
        )
        commentJpaRepository.flush()
        entityManager.clear()

        val result = commentJpaRepository.findAllByPhotoId(photo.nonNullId())

        assertEquals(1, result.size)
        assertNotNull(result[0].user)
        assertEquals(author.nonNullId(), result[0].user!!.nonNullId())
    }

    @Test
    fun `삭제된 댓글은 목록에서 사라지고 복구하면 다시 나타난다`() {
        val photoId = photo.nonNullId()
        val saved =
            commentJpaRepository.save(
                CommentEntity(photo = photo, user = author, content = "댓글", commentedAt = LocalDate.now()),
            )
        commentJpaRepository.flush()
        val commentId = saved.nonNullId()
        entityManager.clear()

        commentJpaRepository.deleteById(commentId)
        commentJpaRepository.flush()
        entityManager.clear()

        assertEquals(0, commentJpaRepository.findAllByPhotoId(photoId).size)

        val restoredCount = commentJpaRepository.restoreDeleted(commentId)
        entityManager.clear()

        assertEquals(1, restoredCount)
        assertEquals(1, commentJpaRepository.findAllByPhotoId(photoId).size)
    }

    @Test
    fun `findOwnerUserIdIncludingDeleted는 삭제된 댓글의 작성자도 조회한다`() {
        val authorId = author.nonNullId()
        val saved =
            commentJpaRepository.save(
                CommentEntity(photo = photo, user = author, content = "댓글", commentedAt = LocalDate.now()),
            )
        commentJpaRepository.flush()
        val commentId = saved.nonNullId()
        entityManager.clear()

        commentJpaRepository.deleteById(commentId)
        commentJpaRepository.flush()
        entityManager.clear()

        assertEquals(authorId, commentJpaRepository.findOwnerUserIdIncludingDeleted(commentId))
    }

    @Test
    fun `삭제되지 않은 댓글을 복구하려 하면 0건이 갱신된다`() {
        val saved =
            commentJpaRepository.save(
                CommentEntity(photo = photo, user = author, content = "댓글", commentedAt = LocalDate.now()),
            )
        commentJpaRepository.flush()
        entityManager.clear()

        val restoredCount = commentJpaRepository.restoreDeleted(saved.nonNullId())

        assertEquals(0, restoredCount)
    }

    @Test
    fun `사진 업로더가 탈퇴해도 findByIdWithRelations는 사진을 계속 찾는다`() {
        val photoId = photo.nonNullId()
        val ownerId = owner.nonNullId()
        entityManager.clear()

        val managedOwner = userJpaRepository.findById(ownerId).get()
        userJpaRepository.delete(managedOwner)
        userJpaRepository.flush()
        entityManager.clear()

        val found = photoJpaRepository.findByIdWithRelations(photoId)

        assertNotNull(found)
        assertNull(found.uploadedBy)
    }
}
