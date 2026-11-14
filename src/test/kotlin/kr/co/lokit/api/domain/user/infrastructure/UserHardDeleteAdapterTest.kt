package kr.co.lokit.api.domain.user.infrastructure

import jakarta.persistence.EntityManager
import kr.co.lokit.api.common.analytics.infrastructure.AppEventLogEntity
import kr.co.lokit.api.domain.album.infrastructure.AlbumEntity
import kr.co.lokit.api.domain.couple.domain.InviteCodeStatus
import kr.co.lokit.api.domain.couple.infrastructure.CoupleEntity
import kr.co.lokit.api.domain.couple.infrastructure.CoupleUserEntity
import kr.co.lokit.api.domain.couple.infrastructure.InviteCodeEntity
import kr.co.lokit.api.domain.map.domain.BoundsIdType
import kr.co.lokit.api.domain.notification.domain.DevicePlatform
import kr.co.lokit.api.domain.notification.domain.NotificationType
import kr.co.lokit.api.domain.notification.infrastructure.DeviceTokenEntity
import kr.co.lokit.api.domain.notification.infrastructure.NotificationEntity
import kr.co.lokit.api.domain.notification.infrastructure.NotificationSettingEntity
import kr.co.lokit.api.domain.notification.infrastructure.PendingUploadNotificationEntity
import kr.co.lokit.api.domain.photo.infrastructure.CommentEntity
import kr.co.lokit.api.domain.photo.infrastructure.PhotoEntity
import kr.co.lokit.api.domain.user.application.port.UserHardDeletePort
import kr.co.lokit.api.fixture.createAlbumBoundsEntity
import kr.co.lokit.api.fixture.createAlbumEntity
import kr.co.lokit.api.fixture.createCommentEntity
import kr.co.lokit.api.fixture.createCoupleEntity
import kr.co.lokit.api.fixture.createEmoticonEntity
import kr.co.lokit.api.fixture.createPhotoEntity
import kr.co.lokit.api.fixture.createRefreshTokenEntity
import kr.co.lokit.api.fixture.createUserEntity
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @SoftDelete 필터를 우회해 행이 물리적으로 사라지는지, 실제 FK 순서로 지워지는지는 실 DB로만 검증할 수 있다.
 * 검증 쿼리도 소프트삭제 행까지 세도록 네이티브 COUNT 를 쓴다.
 */
@DataJpaTest
@Import(JpaUserHardDeleteAdapter::class)
class UserHardDeleteAdapterTest {
    @Autowired
    lateinit var adapter: UserHardDeletePort

    @Autowired
    lateinit var em: EntityManager

    private var seq = 0

    private fun <T : Any> persist(entity: T): T = entity.also { em.persist(it) }

    private fun user(email: String) = persist(createUserEntity(email = email))

    private fun couple(vararg members: UserEntity): CoupleEntity {
        val couple = persist(createCoupleEntity())
        members.forEach { persist(CoupleUserEntity(couple = couple, user = it)) }
        return couple
    }

    private fun album(
        couple: CoupleEntity,
        owner: UserEntity,
    ) = persist(createAlbumEntity(couple = couple, createdBy = owner))

    private fun photo(
        album: AlbumEntity,
        owner: UserEntity,
    ): PhotoEntity =
        persist(
            createPhotoEntity(url = "https://example.com/${++seq}.jpg", album = album, uploadedBy = owner)
                .apply { coupleId = album.couple.nonNullId() },
        )

    private fun comment(
        photo: PhotoEntity,
        owner: UserEntity,
    ): CommentEntity = persist(createCommentEntity(photo = photo, user = owner))

    private fun notification(
        recipientId: Long,
        actorId: Long,
        photoId: Long,
    ) = persist(
        NotificationEntity(
            notifId = "n-${++seq}",
            recipientUserId = recipientId,
            actorUserId = actorId,
            notificationType = NotificationType.COMMENT,
            targetPhotoId = photoId,
            title = "t",
            body = "b",
            sentAt = LocalDateTime.now(),
        ),
    )

    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    private fun softDelete(
        table: String,
        id: Long,
    ) {
        em.createNativeQuery("update $table set is_deleted = true where id = $id").executeUpdate()
    }

    private fun count(tableAndCondition: String): Long =
        (em.createNativeQuery("select count(*) from $tableAndCondition").singleResult as Number).toLong()

    @Test
    fun `사용자와 현재 커플의 앨범, 사진, 댓글, 이모티콘을 물리 삭제하고 사진 URL을 돌려준다`() {
        val me = user("me@test.com")
        val partner = user("partner@test.com")
        val couple = couple(me, partner)
        val album = album(couple, me)
        val myPhoto = photo(album, me)
        val partnerPhoto = photo(album, partner)
        val comment = comment(myPhoto, partner)
        persist(createEmoticonEntity(comment = comment, user = partner))
        persist(createAlbumBoundsEntity(albumId = album.nonNullId(), idType = BoundsIdType.ALBUM))
        persist(createAlbumBoundsEntity(albumId = couple.nonNullId(), idType = BoundsIdType.COUPLE))
        flushAndClear()

        val urls = adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(setOf(myPhoto.url, partnerPhoto.url), urls.toSet())
        assertEquals(0, count("users where id = ${me.nonNullId()}"))
        listOf("couple_user", "couple", "album", "photo", "comment", "emoticon", "album_bounds").forEach {
            assertEquals(0, count(it), it)
        }
    }

    @Test
    fun `파트너 계정은 남고 커플에서만 빠진다`() {
        val me = user("me@test.com")
        val partner = user("partner@test.com")
        couple(me, partner)
        persist(createRefreshTokenEntity(user = partner))
        flushAndClear()

        adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(1, count("users where id = ${partner.nonNullId()}"))
        assertEquals(1, count("refresh_token where user_id = ${partner.nonNullId()}"))
        assertEquals(0, count("couple_user"))
    }

    @Test
    fun `사용자를 참조하는 토큰, 설정, 초대코드, 이벤트 로그, 알림을 함께 삭제한다`() {
        val me = user("me@test.com")
        val partner = user("partner@test.com")
        val myId = me.nonNullId()
        val partnerId = partner.nonNullId()
        persist(createRefreshTokenEntity(user = me))
        persist(DeviceTokenEntity(token = "t1", userId = myId, platform = DevicePlatform.values().first()))
        persist(NotificationSettingEntity(userId = myId, masterEnabled = true, disabledTypes = ""))
        persist(AppEventLogEntity(eventName = "e", userId = myId))
        persist(InviteCodeEntity(code = "AAAAAA", createdBy = me, status = InviteCodeStatus.UNUSED, expiresAt = LocalDateTime.now()))
        persist(InviteCodeEntity(code = "BBBBBB", createdBy = partner, status = InviteCodeStatus.UNUSED, expiresAt = LocalDateTime.now(), usedBy = me))
        notification(recipientId = myId, actorId = partnerId, photoId = 999L)
        notification(recipientId = partnerId, actorId = myId, photoId = 999L)
        persist(PendingUploadNotificationEntity(coupleId = 999L, recipientUserId = partnerId, actorUserId = myId, photoIds = "", scheduledAt = LocalDateTime.now()))
        flushAndClear()

        adapter.hardDelete(listOf(myId))

        listOf(
            "refresh_token",
            "device_token",
            "notification_setting",
            "app_event_log",
            "invite_code",
            "notification",
            "pending_upload_notification",
        ).forEach { assertEquals(0, count(it), it) }
        assertEquals(1, count("users"))
    }

    @Test
    fun `대상 사진을 가리키는 파트너의 알림과 대상 커플의 대기 알림도 삭제한다`() {
        val me = user("me@test.com")
        val partner = user("partner@test.com")
        val other = user("other@test.com")
        val couple = couple(me, partner)
        val photo = photo(album(couple, me), me)
        notification(recipientId = partner.nonNullId(), actorId = other.nonNullId(), photoId = photo.nonNullId())
        val kept = notification(recipientId = partner.nonNullId(), actorId = other.nonNullId(), photoId = 999_999L)
        persist(PendingUploadNotificationEntity(coupleId = couple.nonNullId(), recipientUserId = partner.nonNullId(), actorUserId = other.nonNullId(), photoIds = "", scheduledAt = LocalDateTime.now()))
        flushAndClear()

        adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(1, count("notification"))
        assertEquals(1, count("notification where id = ${kept.nonNullId()}"))
        assertEquals(0, count("pending_upload_notification"))
    }

    @Test
    fun `소프트 삭제된 행도 물리 삭제한다`() {
        val me = user("me@test.com")
        val couple = couple(me)
        val album = album(couple, me)
        val deletedPhoto = photo(album, me)
        val deletedComment = comment(deletedPhoto, me)
        flushAndClear()
        softDelete("comment", deletedComment.nonNullId())
        softDelete("photo", deletedPhoto.nonNullId())
        softDelete("album", album.nonNullId())
        softDelete("couple", couple.nonNullId())
        em.createNativeQuery("update couple_user set is_deleted = true").executeUpdate()
        softDelete("users", me.nonNullId())

        val urls = adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(listOf(deletedPhoto.url), urls)
        listOf("users", "couple_user", "couple", "album", "photo", "comment").forEach { assertEquals(0, count(it), it) }
    }

    @Test
    fun `소프트 삭제된 사용자도 이메일로 찾는다`() {
        val me = user("me@test.com")
        user("other@test.com")
        flushAndClear()
        softDelete("users", me.nonNullId())

        assertEquals(listOf(me.nonNullId()), adapter.findUserIdsByEmail("me@test.com"))
        assertTrue(adapter.findUserIdsByEmail("none@test.com").isEmpty())
    }

    @Test
    fun `과거 커플에 파트너가 남아 있으면 커플과 사진을 유지하고 사용자의 멤버십만 지운다`() {
        val me = user("me@test.com")
        val partner = user("partner@test.com")
        val pastCouple = couple(me, partner)
        val pastPhoto = photo(album(pastCouple, partner), me)
        val current = couple()
        persist(CoupleUserEntity(couple = current, user = me))
        val currentPhoto = photo(album(current, me), me)
        flushAndClear()
        em
            .createNativeQuery("update couple_user set is_deleted = true where couple_id = ${pastCouple.nonNullId()} and user_id = ${me.nonNullId()}")
            .executeUpdate()

        val urls = adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(listOf(currentPhoto.url), urls)
        assertEquals(1, count("couple where id = ${pastCouple.nonNullId()}"))
        assertEquals(1, count("photo where id = ${pastPhoto.nonNullId()}"))
        assertEquals(0, count("couple where id = ${current.nonNullId()}"))
        assertEquals(0, count("couple_user where user_id = ${me.nonNullId()}"))
        assertEquals(1, count("couple_user where user_id = ${partner.nonNullId()}"))
    }

    @Test
    fun `앨범 id와 숫자가 같은 다른 커플의 지도 경계는 지우지 않는다`() {
        val me = user("me@test.com")
        val other = user("other@test.com")
        val otherCouple = couple(other)
        val couple = couple(me)
        val album = album(couple, me)
        persist(createAlbumBoundsEntity(albumId = album.nonNullId(), idType = BoundsIdType.COUPLE))
        persist(createAlbumBoundsEntity(albumId = otherCouple.nonNullId(), idType = BoundsIdType.COUPLE))
        flushAndClear()

        adapter.hardDelete(listOf(me.nonNullId()))

        assertEquals(1, count("album_bounds where standard_id = ${otherCouple.nonNullId()} and id_type = 'COUPLE'"))
    }
}
