package kr.co.lokit.api.domain.photo.domain

import kr.co.lokit.api.fixture.createCouple
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PhotoViewerRoleTest {
    @Test
    fun `뷰어가 업로더면 OWNER 다`() {
        val couple = createCouple(id = 1L, userIds = listOf(UPLOADER_ID, PARTNER_ID))

        val role = PhotoViewerRole.of(uploadedById = UPLOADER_ID, viewerUserId = UPLOADER_ID, couple = couple)

        assertEquals(PhotoViewerRole.OWNER, role)
    }

    @Test
    fun `같은 커플의 상대방이 조회하면 PARTNER 다`() {
        val couple = createCouple(id = 1L, userIds = listOf(UPLOADER_ID, PARTNER_ID))

        val role = PhotoViewerRole.of(uploadedById = UPLOADER_ID, viewerUserId = PARTNER_ID, couple = couple)

        assertEquals(PhotoViewerRole.PARTNER, role)
    }

    @Test
    fun `커플 비멤버가 조회하면 OTHER 다`() {
        // 이 슬라이스의 존재 이유(계약 0-R-2): PermissionService.canReadPhoto 의 isAdmin 우회 경로는
        // 커플 멤버십을 검사하지 않고 통과시킨다. 그 경로로 들어온 뷰어를 partnerIdFor 기반으로
        // 판정하면 userIds[0] 이 반환돼 남의 커플 사진 조회가 PARTNER 로 기록되고,
        // 이 슬라이스가 측정하려는 지표가 그대로 오염된다. 비멤버는 반드시 OTHER 여야 한다.
        val couple = createCouple(id = 1L, userIds = listOf(UPLOADER_ID, PARTNER_ID))

        val role = PhotoViewerRole.of(uploadedById = UPLOADER_ID, viewerUserId = OUTSIDER_ID, couple = couple)

        assertEquals(PhotoViewerRole.OTHER, role)
    }

    @Test
    fun `커플이 없고 뷰어가 업로더면 OWNER 다`() {
        val role = PhotoViewerRole.of(uploadedById = UPLOADER_ID, viewerUserId = UPLOADER_ID, couple = null)

        assertEquals(PhotoViewerRole.OWNER, role)
    }

    @Test
    fun `커플이 없고 뷰어가 업로더가 아니면 OTHER 다`() {
        val role = PhotoViewerRole.of(uploadedById = UPLOADER_ID, viewerUserId = PARTNER_ID, couple = null)

        assertEquals(PhotoViewerRole.OTHER, role)
    }

    companion object {
        private const val UPLOADER_ID = 1L
        private const val PARTNER_ID = 2L
        private const val OUTSIDER_ID = 99L
    }
}
