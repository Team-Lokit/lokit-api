package kr.co.lokit.api.domain.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "로그인 요청")
data class LoginRequest(
    @Schema(description = "사용자 이메일", example = "user@exa***.com", requiredMode = Schema.RequiredMode.REQUIRED)
    val email: String,
)

@Schema(description = "JWT 토큰 응답")
data class JwtTokenResponse(
    @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val accessToken: String,
    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
    val refreshToken: String,
)

@Schema(description = "로그아웃 요청 (본문 선택)")
data class LogoutRequest(
    @field:Size(max = 512, message = "디바이스 토큰은 512자 이내여야 합니다.")
    @Schema(
        description = "로그아웃하는 이 기기의 FCM 등록 토큰. 보내면 이 토큰만 삭제하고, 생략·null·빈 문자열이면 사용자의 디바이스 토큰을 전부 삭제한다.",
        example = "dXJ0Y2hfZmNtX3Rva2VuOkFQQTkxYkg...",
        maxLength = 512,
        nullable = true,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val deviceToken: String? = null,
)
