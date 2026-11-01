package kr.co.lokit.api.domain.photo.infrastructure

import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.user.infrastructure.UserJpaRepository
import kr.co.lokit.api.fixture.createAlbumEntity
import kr.co.lokit.api.fixture.createCoupleEntity
import kr.co.lokit.api.fixture.createPhotoEntity
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D6/B16: 어댑터의 위임 분기만 검증한다.
 * IN 쿼리와 소프트삭제 필터의 실제 동작은 목으로 검증 불가하므로 [PhotoRepositoryTest] 가 실 DB로 맡는다.
 */
@ExtendWith(MockitoExtension::class)
class JpaPhotoRepositoryTest {
    @Mock
    lateinit var photoJpaRepository: PhotoJpaRepository

    @Mock
    lateinit var albumJpaRepository: AlbumJpaRepository

    @Mock
    lateinit var userJpaRepository: UserJpaRepository

    @InjectMocks
    lateinit var jpaPhotoRepository: JpaPhotoRepository

    @Test
    fun `빈 목록을 조회하면 JPA를 호출하지 않는다`() {
        val result = jpaPhotoRepository.findAllByIds(emptyList())

        assertTrue(result.isEmpty())
        verify(photoJpaRepository, never()).findAllByIdsWithRelations(any())
    }

    @Test
    fun `비어있지 않은 목록은 findAllByIdsWithRelations로 위임한다`() {
        val couple = createCoupleEntity(id = 1L)
        val uploader = createUserEntity(id = 1L)
        val album = createAlbumEntity(id = 1L, couple = couple, createdBy = uploader)
        val entities =
            listOf(
                createPhotoEntity(id = 10L, url = "https://example.com/photo-10.jpg", album = album, uploadedBy = uploader),
                createPhotoEntity(id = 20L, url = "https://example.com/photo-20.jpg", album = album, uploadedBy = uploader),
            )
        val ids = listOf(10L, 20L)
        given(photoJpaRepository.findAllByIdsWithRelations(ids)).willReturn(entities)

        val result = jpaPhotoRepository.findAllByIds(ids)

        verify(photoJpaRepository).findAllByIdsWithRelations(ids)
        assertEquals(listOf(10L, 20L), result.map { it.id })
    }
}
