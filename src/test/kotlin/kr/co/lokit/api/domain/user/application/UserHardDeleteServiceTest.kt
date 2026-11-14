package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.photo.domain.PhotoDeletedEvent
import kr.co.lokit.api.domain.user.application.port.UserHardDeletePort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

class UserHardDeleteServiceTest {
    private val port = mock<UserHardDeletePort>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val service = UserHardDeleteService(port, eventPublisher)

    @Test
    fun `이메일의 모든 사용자를 물리 삭제하고 사진 파일 삭제 이벤트를 발행한다`() {
        whenever(port.findUserIdsByEmail("me@test.com")).thenReturn(listOf(1L, 2L))
        whenever(port.hardDelete(listOf(1L, 2L))).thenReturn(listOf("https://a/1.jpg", "https://a/2.jpg"))

        service.hardDeleteByEmail("me@test.com")

        verify(port).hardDelete(listOf(1L, 2L))
        verify(eventPublisher).publishEvent(PhotoDeletedEvent("https://a/1.jpg"))
        verify(eventPublisher).publishEvent(PhotoDeletedEvent("https://a/2.jpg"))
    }

    @Test
    fun `대상 사용자가 없으면 예외가 발생하고 아무것도 지우지 않는다`() {
        whenever(port.findUserIdsByEmail("none@test.com")).thenReturn(emptyList())

        assertThrows<BusinessException.UserNotFoundException> { service.hardDeleteByEmail("none@test.com") }

        verify(port, never()).hardDelete(any())
    }
}
