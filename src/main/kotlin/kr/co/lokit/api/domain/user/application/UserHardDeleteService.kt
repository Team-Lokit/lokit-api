package kr.co.lokit.api.domain.user.application

import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.photo.domain.PhotoDeletedEvent
import kr.co.lokit.api.domain.user.application.port.UserHardDeletePort
import kr.co.lokit.api.domain.user.application.port.`in`.HardDeleteUserUseCase
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserHardDeleteService(
    private val userHardDeletePort: UserHardDeletePort,
    private val eventPublisher: ApplicationEventPublisher,
) : HardDeleteUserUseCase {
    @Transactional
    override fun hardDeleteByEmail(email: String) {
        val userIds = userHardDeletePort.findUserIdsByEmail(email)
        if (userIds.isEmpty()) {
            throw BusinessException.UserNotFoundException(message = "User not found for email: $email")
        }

        // S3 파일은 롤백할 수 없으므로 커밋 후 실행되는 이벤트 핸들러에 맡긴다.
        userHardDeletePort
            .hardDelete(userIds)
            .forEach { url -> eventPublisher.publishEvent(PhotoDeletedEvent(url)) }
    }
}
