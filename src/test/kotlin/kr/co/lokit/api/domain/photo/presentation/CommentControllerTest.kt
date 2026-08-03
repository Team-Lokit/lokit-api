package kr.co.lokit.api.domain.photo.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import kr.co.lokit.api.common.exception.ErrorCode
import kr.co.lokit.api.common.permission.PermissionService
import kr.co.lokit.api.config.security.CompositeAuthenticationResolver
import kr.co.lokit.api.config.security.JwtTokenProvider
import kr.co.lokit.api.config.web.CookieGenerator
import kr.co.lokit.api.config.web.CookieProperties
import kr.co.lokit.api.domain.photo.application.port.`in`.CommentUseCase
import kr.co.lokit.api.domain.photo.application.port.`in`.EmoticonUseCase
import kr.co.lokit.api.domain.photo.domain.CommentWithEmoticons
import kr.co.lokit.api.domain.photo.dto.AddEmoticonRequest
import kr.co.lokit.api.domain.photo.dto.CreateCommentRequest
import kr.co.lokit.api.domain.photo.dto.RemoveEmoticonRequest
import kr.co.lokit.api.domain.photo.dto.UpdateCommentRequest
import kr.co.lokit.api.domain.user.application.AuthService
import kr.co.lokit.api.fixture.createComment
import kr.co.lokit.api.fixture.createEmoticon
import kr.co.lokit.api.fixture.userAuth
import org.hibernate.exception.ConstraintViolationException as HibernateConstraintViolationException
import org.hibernate.exception.DataException
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.SQLException

@WebMvcTest(CommentController::class)
class CommentControllerTest {

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T = org.mockito.ArgumentMatchers.any<T>() as T

    @Autowired
    lateinit var mockMvc: MockMvc

    val objectMapper: ObjectMapper = ObjectMapper()

    @MockitoBean
    lateinit var compositeAuthenticationResolver: CompositeAuthenticationResolver

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @MockitoBean
    lateinit var cookieProperties: CookieProperties

    @MockitoBean
    lateinit var cookieGenerator: CookieGenerator

    @MockitoBean
    lateinit var commentUseCase: CommentUseCase

    @MockitoBean
    lateinit var emoticonUseCase: EmoticonUseCase

    @MockitoBean
    lateinit var permissionService: PermissionService

    @Test
    fun `댓글 생성 성공`() {
        val savedComment = createComment(id = 1L)
        doReturn(savedComment).`when`(commentUseCase).createComment(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            post("/photos/1/comments")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateCommentRequest("멋진 사진!"))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `댓글 생성 실패 - 내용이 비어있음`() {
        mockMvc.perform(
            post("/photos/1/comments")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("content" to ""))),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `댓글 목록 조회 성공`() {
        val comments = listOf(
            CommentWithEmoticons(
                comment = createComment(id = 1L, content = "테스트 댓글"),
                userName = "테스트",
                userProfileImageUrl = null,
                emoticons = emptyList(),
            ),
        )
        doReturn(comments).`when`(commentUseCase).getComments(anyLong(), anyLong())

        mockMvc.perform(
            get("/photos/1/comments")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `답글 생성 성공`() {
        val savedReply = createComment(id = 2L, parentId = 1L, content = "답글")
        doReturn(savedReply).`when`(commentUseCase).createReply(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            post("/photos/comments/1/replies")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateCommentRequest("답글"))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `답글 생성 실패 - 내용이 비어있음`() {
        mockMvc.perform(
            post("/photos/comments/1/replies")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("content" to ""))),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `댓글 수정 성공`() {
        val updated = createComment(id = 1L, content = "수정된 댓글")
        doReturn(updated).`when`(commentUseCase).updateComment(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            put("/photos/comments/1")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(UpdateCommentRequest("수정된 댓글"))),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `댓글 수정 실패 - 500자 초과`() {
        mockMvc.perform(
            put("/photos/comments/1")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("content" to "a".repeat(501)))),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `댓글 삭제 성공`() {
        doNothing().`when`(commentUseCase).deleteComment(anyLong(), anyLong())

        mockMvc.perform(
            delete("/photos/comments/1")
                .with(authentication(userAuth()))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `이모지 추가 성공`() {
        val savedEmoticon = createEmoticon(id = 1L, emoji = "❤️")
        doReturn(savedEmoticon).`when`(emoticonUseCase).addEmoticon(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            post("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AddEmoticonRequest("❤️"))),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `이모지 제거 성공`() {
        doNothing().`when`(emoticonUseCase).removeEmoticon(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            delete("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RemoveEmoticonRequest("❤️"))),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `이모지 추가 중 유니크 제약 위반이 발생하면 500이 아닌 409를 반환한다`() {
        doThrow(
            DataIntegrityViolationException(
                "duplicate key",
                HibernateConstraintViolationException("duplicate key", SQLException(), "uk_emoticon"),
            ),
        ).`when`(emoticonUseCase).addEmoticon(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            post("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AddEmoticonRequest("❤️"))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.DATA_INTEGRITY_VIOLATION.code))
    }

    @Test
    fun `저장 가능한 길이를 넘는 이모지는 500이 아닌 400을 반환한다`() {
        doThrow(
            DataIntegrityViolationException(
                "value too long",
                DataException("value too long", SQLException()),
            ),
        ).`when`(emoticonUseCase).addEmoticon(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            post("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AddEmoticonRequest("👨‍👩‍👧‍👦"))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.INVALID_INPUT.code))
    }

    @Test
    fun `이모지 제거 중 데드락이 발생하면 500이 아닌 409를 반환한다`() {
        doThrow(CannotAcquireLockException("deadlock detected"))
            .`when`(emoticonUseCase).removeEmoticon(anyLong(), anyLong(), anyObject())

        mockMvc.perform(
            delete("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RemoveEmoticonRequest("❤️"))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.LOCK_TIMEOUT.code))
    }

    @Test
    fun `Content-Type 없이 이모지를 추가하면 500이 아닌 415를 반환한다`() {
        mockMvc.perform(
            post("/photos/comments/1/emoticons")
                .with(authentication(userAuth()))
                .with(csrf())
                .content(objectMapper.writeValueAsString(AddEmoticonRequest("❤️"))),
        )
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.code))
    }

    @Test
    fun `인증되지 않은 사용자는 접근할 수 없다`() {
        mockMvc.perform(get("/photos/1/comments"))
            .andExpect(status().isUnauthorized)
    }
}
