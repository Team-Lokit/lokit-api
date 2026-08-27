package kr.co.lokit.api.domain.photo.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.album.infrastructure.AlbumEntity
import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleEntity
import kr.co.lokit.api.domain.couple.infrastructure.CoupleJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleUserEntity
import kr.co.lokit.api.domain.photo.application.port.PhotoRepositoryPort
import kr.co.lokit.api.domain.user.infrastructure.UserEntity
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.fixture.createAlbumEntity
import kr.co.lokit.api.fixture.createCoupleEntity
import kr.co.lokit.api.fixture.createPhotoEntity
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D6/B16 실측. IN 쿼리 + BaseEntity 의 @SoftDelete 자동 필터 + JOIN FETCH 의 상호작용은
 * 목으로 원리적으로 검증 불가하므로 @DataJpaTest 를 쓴다(T6).
 * PhotoEntity.url 은 unique 제약이므로 사진마다 서로 다른 url 을 준다.
 */
@DataJpaTest
@Import(JpaPhotoRepository::class)
class PhotoRepositoryTest {
    @Autowired
    lateinit var repository: PhotoRepositoryPort

    @Autowired
    lateinit var photoJpaRepository: PhotoJpaRepository

    @Autowired
    lateinit var albumJpaRepository: AlbumJpaRepository

    @Autowired
    lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    lateinit var coupleJpaRepository: CoupleJpaRepository

    @Autowired
    lateinit var entityManager: EntityManager

    private lateinit var uploader: UserEntity
    private lateinit var couple: CoupleEntity
    private lateinit var album: AlbumEntity
    private lateinit var photoIds: List<Long>

    private fun flushAndClear() {
        photoJpaRepository.flush()
        entityManager.clear()
    }

    @BeforeEach
    fun setUp() {
        uploader = userJpaRepository.save(createUserEntity(email = "uploader@test.com"))
        couple = createCoupleEntity()
        couple.addUser(CoupleUserEntity(couple = couple, user = uploader))
        couple = coupleJpaRepository.save(couple)
        album = albumJpaRepository.save(createAlbumEntity(couple = couple, createdBy = uploader))

        photoIds =
            (1..3)
                .map { seq ->
                    photoJpaRepository.save(
                        createPhotoEntity(
                            url = "https://example.com/photo-$seq.jpg",
                            album = album,
                            uploadedBy = uploader,
                        ),
                    )
                }.map { it.nonNullId() }
        flushAndClear()
    }

    @Test
    fun `여러 사진을 id로 한 번에 조회한다`() {
        val result = repository.findAllByIds(photoIds)

        assertEquals(photoIds.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `삭제된 사진은 결과에서 조용히 빠진다`() {
        repository.deleteById(photoIds.first())
        flushAndClear()

        val result = repository.findAllByIds(photoIds + MISSING_PHOTO_ID)

        // 소프트삭제된 1건과 애초에 없는 1건 모두 예외 없이 결과에서만 빠진다.
        assertEquals(photoIds.drop(1).toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `반환된 사진은 앨범과 주소 정보를 포함한다`() {
        val result = repository.findAllByIds(photoIds)

        assertTrue(result.isNotEmpty())
        val albumId = album.nonNullId()
        result.forEach { photo ->
            assertEquals(albumId, photo.albumId)
            assertNotNull(photo.address)
        }
    }

    /**
     * R15. 계약 §3.5 스칼라 프로젝션.
     * PhotoEntity.uploadedById 는 uploadedBy 연관과 이중 매핑된 var 라(§7-10) 목으로는 실제 JPQL
     * 프로젝션이 어느 컬럼을 읽는지 검증할 수 없다. 실 DB 에서만 못박히는 동작이다.
     * 반환 타입을 Long 으로 명시해 non-null 계약(§6 "non-null Long")까지 컴파일러가 강제하게 한다.
     */
    @Test
    fun `사진 id 로 업로더 id 를 조회할 수 있다`() {
        val uploaderId: Long = repository.findUploaderIdById(photoIds.first())

        assertEquals(uploader.nonNullId(), uploaderId)
    }

    /**
     * R16. 없으면 null 을 흘려보내지 않고 findById 와 동일하게 예외를 던진다(계약 §3.5, §6).
     * 이 계약이 없으면 CommentService 가 photoOwnerId = 0L 인 comment_view 를 조용히 기록한다.
     */
    @Test
    fun `없는 사진의 업로더를 조회하면 예외가 발생한다`() {
        assertThrows<BusinessException.ResourceNotFoundException> {
            repository.findUploaderIdById(MISSING_PHOTO_ID)
        }
    }

    companion object {
        private const val MISSING_PHOTO_ID: Long = 999_999L
    }
}
