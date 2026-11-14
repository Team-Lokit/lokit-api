package kr.co.lokit.api.domain.photo.infrastructure.mapping

import kr.co.lokit.api.fixture.createAlbumEntity
import kr.co.lokit.api.fixture.createCoupleEntity
import kr.co.lokit.api.fixture.createPhoto
import kr.co.lokit.api.fixture.createPhotoEntity
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PhotoEntityMappingsTest {
    private val album = createAlbumEntity(id = 1L, couple = createCoupleEntity(id = 1L))
    private val user = createUserEntity(id = 1L)

    @Test
    fun `toEntity는 지역 코드를 엔티티에 싣는다`() {
        val photo = createPhoto(address = "서울 강남구").withRegionCode("11680101")

        val entity = photo.toEntity(album, user)

        assertEquals("11680101", entity.regionCode)
    }

    @Test
    fun `toDomain은 엔티티의 지역 코드를 싣는다`() {
        val entity = createPhotoEntity(id = 10L, album = album, uploadedBy = user)
        entity.regionCode = "11680101"

        val photo = entity.toDomain()

        assertEquals("11680101", photo.regionCode)
    }

    @Test
    fun `apply는 지역 코드가 null이면 기존 값을 유지한다`() {
        val entity = createPhotoEntity(id = 10L, album = album, uploadedBy = user)
        entity.regionCode = "11680101"

        entity.apply(createPhoto(address = "서울 강남구"), album, user)

        assertEquals("11680101", entity.regionCode)
    }

    @Test
    fun `apply는 지역 코드가 있으면 덮어쓴다`() {
        val entity = createPhotoEntity(id = 10L, album = album, uploadedBy = user)
        entity.regionCode = "11680101"

        entity.apply(createPhoto(address = "서울 강남구").withRegionCode("11110101"), album, user)

        assertEquals("11110101", entity.regionCode)
    }
}
