package kr.co.lokit.api.config.advice

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ConstraintViolationException
import kr.co.lokit.api.common.dto.ApiResponse
import kr.co.lokit.api.common.dto.ApiResponse.Companion.ErrorDetail
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorCode
import kr.co.lokit.api.config.logging.RequestTrace
import kr.co.lokit.api.config.notification.DiscordNotifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.BindException
import org.springframework.web.ErrorResponseException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.hibernate.exception.ConstraintViolationException as HibernateConstraintViolationException

@RestControllerAdvice
class ErrorControllerAdvice(
    private val discordNotifier: DiscordNotifier?,
    @Value("\${logging.request.verbose:false}") private val verbose: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun buildTraceString(): String? {
        val traces = RequestTrace.snapshot()
        if (traces.isEmpty()) return null
        return buildString {
            traces.forEach { appendLine("├ ${it.method} → ${it.durationMs}ms") }
        }.trimEnd()
    }

    /**
     * 유니크/외래키 등 제약 위반인지 판별한다.
     * [DataIntegrityViolationException.getMostSpecificCause]는 최하위 [java.sql.SQLException]까지 내려가므로
     * 중간 단계의 Hibernate 예외를 찾기 위해 원인 체인을 직접 순회한다.
     */
    private fun isConstraintViolation(ex: DataIntegrityViolationException): Boolean {
        if (ex is DuplicateKeyException) return true
        return generateSequence(ex.cause) { it.cause }
            .any { it is HibernateConstraintViolationException }
    }

    private fun withTraceLog(errors: Map<String, String>? = null): Map<String, String>? {
        if (!verbose) return errors
        val logStr = buildTraceString() ?: return errors
        val merged = errors?.toMutableMap() ?: mutableMapOf()
        merged["trace"] = logStr
        return merged
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<ErrorDetail> {
        response.status = ex.errorCode.status.value()

        return ApiResponse.failure(
            exception = ex,
            request = request,
            errorCode = ex.errorCode,
            errors = withTraceLog(ex.errors.ifEmpty { null }),
        )
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = ErrorCode.INVALID_INPUT.message,
            request = request,
            errorCode = ErrorCode.INVALID_INPUT.code,
            errors =
                withTraceLog(
                    ex.bindingResult.fieldErrors.associate {
                        it.field to (it.defaultMessage ?: ex::class.java.name)
                    },
                ),
        )

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> {
        val errors =
            ex.bindingResult.fieldErrors.associate {
                it.field to (it.defaultMessage ?: ex::class.java.name)
            }

        return ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = ErrorCode.INVALID_INPUT.message,
            request = request,
            errorCode = ErrorCode.INVALID_INPUT.code,
            errors = withTraceLog(errors),
        )
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = "${ex.parameterName} 파라미터가 필요합니다",
            request = request,
            errorCode = ErrorCode.MISSING_PARAMETER.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = "${ex.name} 파라미터의 타입이 올바르지 않습니다",
            request = request,
            errorCode = ErrorCode.INVALID_TYPE.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요",
            request = request,
            errorCode = ErrorCode.INVALID_INPUT.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleHttpRequestMethodNotSupportedException(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            detail = "${ex.method} 메서드는 지원하지 않습니다",
            request = request,
            errorCode = ErrorCode.METHOD_NOT_ALLOWED.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(
        ex: NoResourceFoundException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.NOT_FOUND,
            detail = "요청한 리소스를 찾을 수 없습니다",
            request = request,
            errorCode = ErrorCode.RESOURCE_NOT_FOUND.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockException(
        ex: ObjectOptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> {
        log.warn("Optimistic lock conflict: ${ex.message}")
        return ApiResponse.failure(
            status = HttpStatus.CONFLICT,
            detail = ErrorCode.CONFLICT.message,
            request = request,
            errorCode = ErrorCode.CONFLICT.code,
            errors = withTraceLog(),
        )
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> {
        val errors =
            ex.constraintViolations.associate {
                it.propertyPath.toString().substringAfterLast('.') to it.message
            }

        return ApiResponse.failure(
            status = HttpStatus.BAD_REQUEST,
            detail = ErrorCode.INVALID_INPUT.message,
            request = request,
            errorCode = ErrorCode.INVALID_INPUT.code,
            errors = withTraceLog(errors.ifEmpty { null }),
        )
    }

    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupportedException(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            detail = "${ex.contentType ?: "요청"} 타입은 지원하지 않습니다. Content-Type: application/json 으로 요청해주세요",
            request = request,
            errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE.code,
            errors = withTraceLog(),
        )

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleHttpMediaTypeNotAcceptableException(
        ex: HttpMediaTypeNotAcceptableException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<ErrorDetail>> =
        ResponseEntity
            .status(HttpStatus.NOT_ACCEPTABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                ApiResponse.failure(
                    status = HttpStatus.NOT_ACCEPTABLE,
                    detail = ErrorCode.NOT_ACCEPTABLE.message,
                    request = request,
                    errorCode = ErrorCode.NOT_ACCEPTABLE.code,
                    errors = withTraceLog(),
                ),
            )

    /**
     * 유니크/외래키 제약 위반은 409, 값 길이 초과 등 데이터 형식 위반은 400으로 내려준다.
     * 예) 이모지 동시 추가로 (comment_id, user_id, emoji) 유니크 제약이 깨지는 경쟁 상태가 여기로 들어온다.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<ErrorDetail> {
        val errorCode = if (isConstraintViolation(ex)) ErrorCode.DATA_INTEGRITY_VIOLATION else ErrorCode.INVALID_INPUT
        response.status = errorCode.status.value()

        log.warn("Data integrity violation on {} {}: {}", request.method, request.requestURI, ex.mostSpecificCause.message)
        return ApiResponse.failure(
            status = errorCode.status,
            detail = errorCode.message,
            request = request,
            errorCode = errorCode.code,
            errors = withTraceLog(),
        )
    }

    /**
     * 낙관적 락 외의 동시성 실패(데드락, 락 획득 실패 등). 재시도 가능한 상황이므로 409로 내려준다.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ConcurrencyFailureException::class)
    fun handleConcurrencyFailureException(
        ex: ConcurrencyFailureException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> {
        log.warn("Concurrency failure on {} {}: {}", request.method, request.requestURI, ex.message)
        return ApiResponse.failure(
            status = HttpStatus.CONFLICT,
            detail = ErrorCode.LOCK_TIMEOUT.message,
            request = request,
            errorCode = ErrorCode.LOCK_TIMEOUT.code,
            errors = withTraceLog(),
        )
    }

    @ExceptionHandler(ErrorResponseException::class)
    fun handleErrorResponseException(
        ex: ErrorResponseException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ApiResponse<ErrorDetail> {
        val status = HttpStatus.valueOf(ex.statusCode.value())
        response.status = status.value()

        return ApiResponse.failure(
            status = status,
            detail = ex.body.detail ?: status.reasonPhrase,
            request = request,
            errorCode = if (status.is5xxServerError) ErrorCode.INTERNAL_SERVER_ERROR.code else ErrorCode.INVALID_INPUT.code,
            errors = withTraceLog(),
        )
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.UNAUTHORIZED,
            detail = ex.message ?: ErrorCode.UNAUTHORIZED.message,
            request = request,
            errorCode = ErrorCode.UNAUTHORIZED.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> =
        ApiResponse.failure(
            status = HttpStatus.FORBIDDEN,
            detail = ex.message ?: ErrorCode.FORBIDDEN.message,
            request = request,
            errorCode = ErrorCode.FORBIDDEN.code,
            errors = withTraceLog(),
        )

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception::class)
    fun handleException(
        ex: Exception,
        request: HttpServletRequest,
    ): ApiResponse<ErrorDetail> {
        log.error("Unhandled exception occurred: {}", ex.message)
        val traceLog = buildTraceString()
        discordNotifier?.notify(ex, request, traceLog)
        return ApiResponse.failure(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            detail = ErrorCode.INTERNAL_SERVER_ERROR.message,
            request = request,
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR.code,
            errors = withTraceLog(),
        )
    }
}
