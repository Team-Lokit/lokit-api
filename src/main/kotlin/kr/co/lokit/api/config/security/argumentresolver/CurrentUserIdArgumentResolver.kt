package kr.co.lokit.api.config.security

import kr.co.lokit.api.common.annotation.CurrentUserId
import kr.co.lokit.api.common.exception.BusinessException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
            (parameter.parameterType == Long::class.java || parameter.parameterType == Long::class.javaObjectType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long? {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? UserPrincipal

        // Kotlin nullable(Long?) 파라미터는 인증이 없으면 null 을 받는다.
        if (principal == null && parameter.isOptional) return null

        if (authentication == null) throw BusinessException.UnauthorizedException()
        return principal?.id ?: throw BusinessException.UnauthorizedException("Invalid authentication principal")
    }
}
