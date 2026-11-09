package kr.co.lokit.api.domain.user.domain

import kr.co.lokit.api.common.constants.AccountStatus
import kr.co.lokit.api.common.constants.UserRole
import java.time.LocalDateTime

data class User(
    val id: Long = 0,
    val email: String,
    val name: String,
    val role: UserRole = UserRole.USER,
    var profileImageUrl: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val withdrawnAt: LocalDateTime? = null,
    val joinedAt: LocalDateTime? = null
) {
    fun withNickname(nickname: String): User = copy(name = nickname)

    fun withProfileImage(profileImageUrl: String?): User = copy(profileImageUrl = profileImageUrl)

    companion object {
        private const val EMAIL_LOCK_KEY_PREFIX = "email:"
        private const val DEFAULT_NICKNAME = "사용자"
        private const val MAX_NICKNAME_LENGTH = 10

        fun emailLockKey(email: String): String = "$EMAIL_LOCK_KEY_PREFIX$email"

        /** 소셜 계정의 이름을 우선 쓰고, 없으면 이메일로 만든다. */
        fun defaultNickname(
            name: String?,
            email: String,
        ): String =
            name
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.truncateToNicknameLength()
                ?: defaultNicknameFor(email)

        private fun String.truncateToNicknameLength(): String {
            val truncated = take(MAX_NICKNAME_LENGTH)
            // 이모지 같은 서로게이트 쌍이 경계에서 잘리면 깨진 문자가 저장되므로 앞쪽 절반도 버린다.
            return (if (truncated.last().isHighSurrogate()) truncated.dropLast(1) else truncated).trimEnd()
        }

        fun defaultNicknameFor(email: String): String =
            email
                .substringBefore("@")
                .trim()
                .ifBlank { DEFAULT_NICKNAME }
                .take(MAX_NICKNAME_LENGTH)
    }
}
