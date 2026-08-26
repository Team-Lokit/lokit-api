package kr.co.lokit.api.domain.photo.application.port

import kr.co.lokit.api.domain.photo.domain.Photo
import kr.co.lokit.api.domain.photo.domain.PhotoDetail

interface PhotoRepositoryPort {
    fun save(photo: Photo): Photo

    fun findDetailById(id: Long): PhotoDetail

    fun deleteById(id: Long)

    fun findAllByUserId(userId: Long): List<Photo>

    fun findById(id: Long): Photo

    fun update(photo: Photo): Photo

    fun saveAll(photos: List<Photo>): List<Photo>

    fun countByCoupleId(coupleId: Long): Long

    fun findPhotoUrlByCoupleIdWithOffset(
        coupleId: Long,
        offset: Int,
    ): String?

    /** D6: 없거나 소프트삭제된 id는 예외 없이 결과에서 빠진다. 빈 입력이면 DB를 조회하지 않는다. 순서 미보장. */
    fun findAllByIds(ids: List<Long>): List<Photo>
}
