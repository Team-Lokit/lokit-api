package kr.co.lokit.api.domain.calendar.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.domain.album.infrastructure.AlbumEntity
import kr.co.lokit.api.domain.album.infrastructure.AlbumJpaRepository
import kr.co.lokit.api.domain.calendar.application.port.CalendarPhotoQueryPort
import kr.co.lokit.api.domain.couple.infrastructure.CoupleEntity
import kr.co.lokit.api.domain.couple.infrastructure.CoupleJpaRepository
import kr.co.lokit.api.domain.couple.infrastructure.CoupleUserEntity
import kr.co.lokit.api.domain.photo.infrastructure.PhotoJpaRepository
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
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * 커플 범위·소프트삭제 필터·반열린 구간·정렬은 실제 쿼리로만 검증 가능하므로 @DataJpaTest 를 쓴다.
 */
@DataJpaTest
@Import(JpaCalendarPhotoQueryAdapter::class)
class CalendarPhotoQueryAdapterTest {
    @Autowired
    lateinit var adapter: CalendarPhotoQueryPort

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
    private var seq = 0

    private val from = LocalDateTime.of(2026, 11, 1, 0, 0)
    private val to = LocalDateTime.of(2026, 12, 1, 0, 0)

    @BeforeEach
    fun setUp() {
        uploader = userJpaRepository.save(createUserEntity(email = "uploader@test.com"))
        couple = createCoupleEntity()
        couple.addUser(CoupleUserEntity(couple = couple, user = uploader))
        couple = coupleJpaRepository.save(couple)
        album = albumJpaRepository.save(createAlbumEntity(couple = couple, createdBy = uploader))
    }

    private fun savePhoto(
        takenAt: LocalDateTime,
        targetAlbum: AlbumEntity = album,
        user: UserEntity = uploader,
    ): Long {
        seq++
        val entity =
            createPhotoEntity(
                url = "https://example.com/photo-$seq.jpg",
                takenAt = takenAt,
                album = targetAlbum,
                uploadedBy = user,
            ).apply { coupleId = targetAlbum.couple.nonNullId() }
        return photoJpaRepository.save(entity).nonNullId()
    }

    private fun flushAndClear() {
        photoJpaRepository.flush()
        entityManager.clear()
    }

    @Test
    fun `기간 안의 커플 사진을 기준 시각 최신순, 같으면 id 내림차순으로 조회한다`() {
        val t = LocalDateTime.of(2026, 11, 2, 9, 0)
        val a = savePhoto(t)
        val b = savePhoto(t.plusHours(3))
        val c = savePhoto(t)
        flushAndClear()

        val result = adapter.findByCoupleIdAndTakenAtRange(couple.nonNullId(), from, to)

        assertEquals(listOf(b, c, a), result.map { it.id })
    }

    @Test
    fun `시작 시각은 포함하고 끝 시각은 제외한다`() {
        val included = savePhoto(from)
        savePhoto(to)
        savePhoto(from.minusNanos(1_000))
        val lastMoment = savePhoto(to.minusSeconds(1))
        flushAndClear()

        val result = adapter.findByCoupleIdAndTakenAtRange(couple.nonNullId(), from, to)

        assertEquals(setOf(included, lastMoment), result.map { it.id }.toSet())
    }

    @Test
    fun `다른 커플의 사진은 조회하지 않는다`() {
        val otherUser = userJpaRepository.save(createUserEntity(email = "other@test.com"))
        var otherCouple = createCoupleEntity(name = "다른")
        otherCouple.addUser(CoupleUserEntity(couple = otherCouple, user = otherUser))
        otherCouple = coupleJpaRepository.save(otherCouple)
        val otherAlbum = albumJpaRepository.save(createAlbumEntity(couple = otherCouple, createdBy = otherUser))
        val mine = savePhoto(LocalDateTime.of(2026, 11, 5, 10, 0))
        savePhoto(LocalDateTime.of(2026, 11, 5, 11, 0), otherAlbum, otherUser)
        flushAndClear()

        val result = adapter.findByCoupleIdAndTakenAtRange(couple.nonNullId(), from, to)

        assertEquals(listOf(mine), result.map { it.id })
    }

    @Test
    fun `삭제된 사진은 조회하지 않는다`() {
        val alive = savePhoto(LocalDateTime.of(2026, 11, 5, 10, 0))
        val deleted = savePhoto(LocalDateTime.of(2026, 11, 5, 11, 0))
        flushAndClear()
        photoJpaRepository.deleteById(deleted)
        flushAndClear()

        val result = adapter.findByCoupleIdAndTakenAtRange(couple.nonNullId(), from, to)

        assertEquals(listOf(alive), result.map { it.id })
    }

    @Test
    fun `사진의 url과 기준 시각, 주소를 함께 조회한다`() {
        val takenAt = LocalDateTime.of(2026, 11, 7, 18, 30)
        val id = savePhoto(takenAt)
        flushAndClear()

        val photo = adapter.findByCoupleIdAndTakenAtRange(couple.nonNullId(), from, to).single()

        assertEquals(id, photo.id)
        assertEquals("https://example.com/photo-$seq.jpg", photo.url)
        assertEquals(takenAt, photo.takenAt)
        assertEquals("서울 강남구", photo.address)
    }
}
