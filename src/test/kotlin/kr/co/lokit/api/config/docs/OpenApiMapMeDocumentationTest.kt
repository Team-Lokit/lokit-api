package kr.co.lokit.api.config.docs

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@SpringBootTest(
    properties = [
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
    ],
)
@AutoConfigureMockMvc
class OpenApiMapMeDocumentationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper: ObjectMapper = ObjectMapper()

    @Test
    fun `map me 문서는 v1_1 기준으로 단일 노출되고 기본 버전 안내를 포함한다`() {
        val openApiJson = fetchOpenApiJson()
        val openApi = objectMapper.readTree(openApiJson)
        writeOpenApiArtifact(openApiJson)

        assertTrue(openApi["openapi"].asText().startsWith("3.1"))

        val mapMeGet = openApi["paths"]["/map/me"]["get"]
        assertNotNull(mapMeGet)
        assertEquals("Map", mapMeGet["tags"][0].asText())

        val versionHeader =
            mapMeGet["parameters"]
                .firstOrNull { it["name"].asText() == "X-API-VERSION" && it["in"].asText() == "header" }
        assertNotNull(versionHeader)
        assertEquals("1.1", versionHeader["schema"]["enum"][0].asText())
        assertEquals("1.0.0", versionHeader["schema"]["default"].asText())

        val description = mapMeGet["description"].asText()
        assertTrue(description.contains("v1.0.0은 Deprecated 상태인 기본 버전"))
        assertTrue(description.contains("헤더 없이 호출 가능합니다"))
        assertTrue(description.contains("X-API-VERSION=1.1"))
    }

    private fun fetchOpenApiJson(): String {
        val candidatePaths =
            listOf(
                "/v3/api-docs/api-v1.0",
                "/docs/api-v1.0",
                "/api/v3/api-docs/api-v1.0",
                "/api/docs/api-v1.0",
                "/v3/api-docs",
                "/docs",
                "/api/v3/api-docs",
                "/api/docs",
            )

        val responses =
            candidatePaths.associateWith { path ->
                mockMvc.perform(get(path)).andReturn().response
            }

        val response =
            responses.values.firstOrNull { it.status == 200 }
                ?: error(
                    "OpenAPI docs endpoint not found. statuses=${
                        responses.map { (path, response) -> "$path:${response.status}" }
                    }",
                )

        return response.contentAsString
    }

    private fun writeOpenApiArtifact(openApiJson: String) {
        val outputDir = Path.of("build", "openapi")
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("api-v1.0.json"), openApiJson)
    }
}
