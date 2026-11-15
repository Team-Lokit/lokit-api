package kr.co.lokit.api.domain.photo.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.LocalDateTime
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.domain.photo.domain.PhotoDetailReadModel
import kr.co.lokit.api.fixture.createPhoto
import kr.co.lokit.api.fixture.createPhotoRequest
import kr.co.lokit.api.fixture.createUpdatePhotoRequest
import kr.co.lokit.api.fixture.userAuth
import kr.co.lokit.api.support.ControllerSliceTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PhotoControllerTest : ControllerSliceTest() {
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T = org.mockito.ArgumentMatchers.any<T>() as T

    val objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun `사진 생성 성공`() {
        val savedPhoto = createPhoto(id = 1L)
        doReturn(savedPhoto).`when`(createPhotoUseCase).create(anyObject())

        mockMvc.perform(
            post("/photos")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        createPhotoRequest(takenAt = LocalDateTime.of(2026, 1, 1, 12, 0)),
                    ),
                ),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `촬영 시각 없이도 사진을 생성할 수 있다`() {
        doReturn(createPhoto(id = 1L)).`when`(createPhotoUseCase).create(anyObject())

        mockMvc.perform(
            post("/photos")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://example.com/photo.jpg","albumId":1,"longitude":127.0,"latitude":37.5}"""),
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `사진 상세 조회 성공`() {
        val response = PhotoDetailReadModel(
            id = 1L,
            url = "https://example.com/photo.jpg",
            takenAt = LocalDateTime.of(2026, 1, 1, 12, 0),
            albumName = "여행",
            uploadedById = 1L,
            uploaderName = "테스트",
            uploaderProfileImageUrl = null,
            address = "서울 강남구",
            description = "테스트",
            longitude = 127.0276,
            latitude = 37.4979,
        )
        doReturn(response).`when`(getPhotoDetailUseCase).getPhotoDetail(anyLong(), anyLong())

        mockMvc.perform(
            get("/photos/1")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `행정구역 밖 좌표 업로드는 400과 PHOTO_002를 반환한다`() {
        doThrow(BusinessException.PhotoLocationOutOfRegionException())
            .`when`(createPhotoUseCase).create(anyObject())

        mockMvc.perform(
            post("/photos")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        createPhotoRequest(takenAt = LocalDateTime.of(2026, 1, 1, 12, 0)),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.data.errorCode").value("PHOTO_002"))
    }

    @Test
    fun `존재하지 않는 사진 상세 조회 실패`() {
        doThrow(BusinessException.ResourceNotFoundException("Photo(id=999)을(를) 찾을 수 없습니다"))
            .`when`(getPhotoDetailUseCase).getPhotoDetail(anyLong(), anyLong())

        mockMvc.perform(
            get("/photos/999")
                .with(authentication(userAuth())),
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `사진 수정 성공`() {
        val updatedPhoto = createPhoto(id = 1L, description = "수정됨")
        doReturn(updatedPhoto).`when`(updatePhotoUseCase)
            .update(anyLong(), anyLong(), anyObject(), anyDouble(), anyDouble(), anyLong())

        mockMvc.perform(
            put("/photos/1")
                .with(authentication(userAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUpdatePhotoRequest(1L, 0.0, 0.0, description = "수정됨"))),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `사진 삭제 성공`() {
        doNothing().`when`(updatePhotoUseCase).delete(anyLong(), anyLong())

        mockMvc.perform(
            delete("/photos/1")
                .with(authentication(userAuth()))
                .with(csrf()),
        )
            .andExpect(status().isNoContent)
    }

    @Test
    fun `인증되지 않은 사용자는 접근할 수 없다`() {
        mockMvc.perform(get("/photos/1"))
            .andExpect(status().isUnauthorized)
    }
}
