package kr.co.lokit.api.config.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.slf4j.LoggerFactory

class LoggingInterceptorTest {

    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        appender.start()
        (LoggerFactory.getLogger(LoggingInterceptor::class.java) as Logger).addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        (LoggerFactory.getLogger(LoggingInterceptor::class.java) as Logger).detachAppender(appender)
        RequestTrace.drain()
    }

    private fun mockRequest(
        method: String = "GET",
        uri: String = "/photos/1/comments",
        query: String? = null,
    ): HttpServletRequest {
        val request = mock(HttpServletRequest::class.java)
        `when`(request.method).thenReturn(method)
        `when`(request.requestURI).thenReturn(uri)
        `when`(request.queryString).thenReturn(query)
        `when`(request.getAttribute(MdcContextFilter.START_TIME_ATTR)).thenReturn(System.currentTimeMillis())
        return request
    }

    private fun mockResponse(status: Int): HttpServletResponse {
        val response = mock(HttpServletResponse::class.java)
        `when`(response.status).thenReturn(status)
        return response
    }

    @Test
    fun `compact 모드는 method, uri, status, latency를 메시지에 포함한다`() {
        val interceptor = LoggingInterceptor(verbose = false)
        val request = mockRequest(method = "GET", uri = "/photos/1/comments")
        val response = mockResponse(200)

        interceptor.afterCompletion(request, response, Any(), null)

        val message = appender.list.single().formattedMessage
        assertTrue(message.startsWith("GET /photos/1/comments → 200 ("), "실제 메시지: $message")
        assertTrue(message.contains("ms)"), "실제 메시지: $message")
    }

    @Test
    fun `compact 모드는 트레이스가 없으면 트레이스 요약을 붙이지 않는다`() {
        val interceptor = LoggingInterceptor(verbose = false)
        val request = mockRequest()
        val response = mockResponse(200)

        interceptor.afterCompletion(request, response, Any(), null)

        val message = appender.list.single().formattedMessage
        assertFalse(message.contains("calls="), "실제 메시지: $message")
    }

    @Test
    fun `compact 모드는 요청 트레이스가 있으면 호출 수, 총 소요시간, 가장 느린 호출을 요약한다`() {
        RequestTrace.init()
        RequestTrace.add("CommentService.createComment", 5)
        RequestTrace.add("JpaCommentRepository.save", 12)

        val interceptor = LoggingInterceptor(verbose = false)
        val request = mockRequest()
        val response = mockResponse(201)

        interceptor.afterCompletion(request, response, Any(), null)

        val message = appender.list.single().formattedMessage
        assertTrue(message.contains("[calls=2, traceMs=17, slowest=JpaCommentRepository.save(12ms)]"), "실제 메시지: $message")
    }

    @Test
    fun `compact 모드에서 트레이스는 로그로 남긴 뒤 비운다`() {
        RequestTrace.init()
        RequestTrace.add("CommentService.createComment", 5)

        val interceptor = LoggingInterceptor(verbose = false)
        interceptor.afterCompletion(mockRequest(), mockResponse(200), Any(), null)

        assertEquals(0, RequestTrace.snapshot().size)
    }

    @Test
    fun `5xx 응답은 error 레벨로 기록된다`() {
        val interceptor = LoggingInterceptor(verbose = false)
        interceptor.afterCompletion(mockRequest(), mockResponse(500), Any(), null)

        assertEquals(ch.qos.logback.classic.Level.ERROR, appender.list.single().level)
    }

    @Test
    fun `4xx 응답은 warn 레벨로 기록된다`() {
        val interceptor = LoggingInterceptor(verbose = false)
        interceptor.afterCompletion(mockRequest(), mockResponse(404), Any(), null)

        assertEquals(ch.qos.logback.classic.Level.WARN, appender.list.single().level)
    }
}
