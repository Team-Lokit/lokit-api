package kr.co.lokit.api.domain.user.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kr.co.lokit.api.domain.user.dto.LogoutRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Auth", description = "인증 API")
interface AuthApi {
    @Operation(
        summary = "카카오 로그인 페이지로 리다이렉트",
        description = "카카오 OAuth 인증 페이지로 리다이렉트합니다. redirect 파라미터로 로그인 후 돌아갈 프론트엔드 URL을 지정할 수 있습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "302",
                description = "카카오 인증 페이지로 리다이렉트",
            ),
        ],
    )
    @SecurityRequirements
    fun kakaoAuthorize(
        @Parameter(description = "로그인 후 리다이렉트할 프론트엔드 URL", example = "https://developer.co.kr")
        redirect: String?,
        @Parameter(hidden = true) req: HttpServletRequest,
    ): ResponseEntity<Unit>

    @Operation(
        summary = "애플 로그인 페이지로 리다이렉트",
        description = "애플 OAuth 인증 페이지로 리다이렉트합니다. redirect 파라미터로 로그인 후 돌아갈 프론트엔드 URL을 지정할 수 있습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "302",
                description = "카카오 인증 페이지로 리다이렉트",
            ),
        ],
    )
    @SecurityRequirements
    fun appleAuthorize(
        @Parameter(description = "로그인 후 리다이렉트할 프론트엔드 URL", example = "https://developer.co.kr")
        redirect: String?,
        @Parameter(hidden = true) req: HttpServletRequest,
    ): ResponseEntity<Unit>

    @Operation(hidden = true)
    fun kakaoCallback(
        @RequestParam code: String,
        @RequestParam(required = false) state: String?,
        @Parameter(hidden = true) req: HttpServletRequest,
    ): ResponseEntity<Unit>

    @Operation(hidden = true)
    fun appleCallback(
        @RequestParam code: String,
        @RequestParam(required = false) state: String?,
        @Parameter(hidden = true) req: HttpServletRequest,
    ): ResponseEntity<Unit>

    @SecurityRequirement(name = "Authorization")
    @Operation(
        summary = "로그아웃",
        description =
            "현재 로그인된 사용자의 세션(리프레시 토큰, 인증 쿠키)을 종료하고 푸시 디바이스 토큰을 삭제합니다.\n\n" +
                "- 요청 본문은 **선택**입니다.\n" +
                "- `deviceToken` 을 보내면 **이 기기의 토큰만** 삭제합니다. 같은 계정의 다른 기기는 계속 푸시를 받습니다.\n" +
                "- 본문을 생략하거나 `deviceToken` 이 null·빈 문자열이면 사용자의 디바이스 토큰을 **전부** 삭제합니다(기존 동작).\n" +
                "- 본문을 보낼 때는 `Content-Type: application/json` 을 사용하세요.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            ApiResponse(responseCode = "400", description = "deviceToken 이 512자를 초과", content = [Content()]),
            ApiResponse(responseCode = "401", description = "인증 필요", content = [Content()]),
        ],
    )
    fun logout(
        @Parameter(hidden = true) userId: Long,
        @SwaggerRequestBody(
            required = false,
            description = "선택. 이 기기의 FCM 토큰을 담으면 해당 기기 토큰만 삭제합니다.",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = LogoutRequest::class),
                    examples = [
                        ExampleObject(
                            name = "이 기기 토큰만 삭제",
                            value = """{"deviceToken": "dXJ0Y2hfZmNtX3Rva2VuOkFQQTkxYkg..."}""",
                        ),
                        ExampleObject(
                            name = "전체 기기 토큰 삭제",
                            summary = "본문을 생략한 것과 같다",
                            value = """{}""",
                        ),
                    ],
                ),
            ],
        )
        request: LogoutRequest?,
        @Parameter(hidden = true) req: HttpServletRequest,
        @Parameter(hidden = true) res: HttpServletResponse,
    )
}
