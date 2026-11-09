package kr.co.lokit.api.domain.user.application.port.`in`

interface HardDeleteUserUseCase {
    fun hardDeleteByEmail(email: String)
}
