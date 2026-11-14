package kr.co.lokit.api.domain.photo.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleUserEntity
import kr.co.lokit.api.domain.photo.domain.Emoticon
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
import kotlin.test.assertTrue

@DataJpaTest
@Import(JpaEmoticonRepository::class)
class JpaEmoticonRepositoryTest {
    @Autowired
    lateinit var emoticonRepository: JpaEmoticonRepository

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

    lateinit var user: UserEntity
    lateinit var comment: CommentEntity

    @BeforeEach
    fun setUp() {
        user = userJpaRepository.save(createUserEntity(email = "reactor@test.com"))
        var couple = createCoupleEntity()
        couple.addUser(CoupleUserEntity(couple = couple, user = user))
        couple = coupleJpaRepository.save(couple)
        val album = albumJpaRepository.save(createAlbumEntity(couple = couple, createdBy = user))
        val photo = photoJpaRepository.save(createPhotoEntity(album = album, uploadedBy = user))
        comment =
            commentJpaRepository.save(
                CommentEntity(photo = photo, user = user, content = "댓글", commentedAt = LocalDate.now()),
            )
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `삭제했던 이모티콘을 같은 댓글에 다시 추가할 수 있다`() {
        val commentId = comment.nonNullId()
        val userId = user.nonNullId()
        val emoticon = Emoticon(commentId = commentId, userId = userId, emoji = "❤️")

        emoticonRepository.save(emoticon)
        entityManager.flush()
        entityManager.clear()
        emoticonRepository.delete(commentId, userId, "❤️")
        entityManager.flush()
        entityManager.clear()

        emoticonRepository.save(emoticon)
        entityManager.flush()
        entityManager.clear()

        assertTrue(emoticonRepository.existsByCommentIdAndUserIdAndEmoji(commentId, userId, "❤️"))
    }
}
