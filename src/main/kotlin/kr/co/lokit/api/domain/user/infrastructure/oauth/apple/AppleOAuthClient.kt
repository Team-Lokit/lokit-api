package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import io.jsonwebtoken.Jwts
import kr.co.lokit.api.common.exception.BusinessException
import kr.co.lokit.api.common.exception.ErrorField
import kr.co.lokit.api.common.exception.errorDetailsOf
import kr.co.lokit.api.domain.user.application.port.OAuthProvider
import kr.co.lokit.api.domain.user.application.port.OAuthUserInfo
import kr.co.lokit.api.domain.user.dto.AppleTokenResponse
import kr.co.lokit.api.domain.user.infrastructure.oauth.OAuthClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*

@Component
@EnableConfigurationProperties(AppleOAuthProperties::class)
class AppleOAuthClient(
    private val properties: AppleOAuthProperties,
    private val clientSecretGenerator: AppleClientSecretGenerator,
    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5_000)
                    setReadTimeout(10_000)
                },
            ).build(),
) : OAuthClient {
    override val provider: OAuthProvider = OAuthProvider.APPLE

    @Retryable(
        retryFor = [BusinessException.AppleApiException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 200, multiplier = 2.0, random = true),
    )
    override fun getAccessToken(code: String): String {
        val formData =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", properties.clientId)
                add("client_secret", clientSecretGenerator.generate())
                add("redirect_uri", properties.redirectUri)
                add("code", code)
            }

        val response =
            try {
                restClient
                    .post()
                    .uri(AppleOAuthProperties.TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError) { _, res ->
                        throw BusinessException.InvalidAppleTokenException(
                            message = "애플 인가 코드가 유효하지 않습니다 (status: ${res.statusCode})",
                            errors = errorDetailsOf(ErrorField.STATUS_CODE to res.statusCode.value()),
                        )
                    }.body(AppleTokenResponse::class.java)
                    ?: throw BusinessException.AppleApiException(
                        message = "애플 토큰 응답을 파싱할 수 없습니다",
                    )
            } catch (e: BusinessException.InvalidAppleTokenException) {
                throw e
            } catch (e: RestClientException) {
                throw BusinessException.AppleApiException(
                    message = "애플 토큰 API 호출이 실패했습니다",
                    cause = e,
                )
            }

        return response.idToken
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val publicKey = fetchApplePublicKey(accessToken)

        val claims =
            try {
                Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer("https://appleid.apple.com")
                    .requireAudience(properties.clientId)
                    .build()
                    .parseSignedClaims(accessToken)
                    .payload
            } catch (e: Exception) {
                throw BusinessException.InvalidAppleTokenException(
                    message = "애플 id_token 검증에 실패했습니다",
                    cause = e,
                )
            }

        return AppleOAuthUserInfo(
            providerId = claims.subject,
            email = claims["email", String::class.java],
        )
    }

    private fun fetchApplePublicKey(idToken: String): PublicKey {
        val header = parseJwtHeader(idToken)
        val kid = header["kid"] as? String
            ?: throw BusinessException.InvalidAppleTokenException(
                message = "적합한 애플 id_token에 kid가 없습니다",
            )

        val keys =
            try {
                restClient
                    .get()
                    .uri(AppleOAuthProperties.PUBLIC_KEYS_URL)
                    .retrieve()
                    .body(ApplePublicKeyResponse::class.java)
                    ?: throw BusinessException.AppleApiException(
                        message = "애플 공개키 응답을 파싱할 수 없습니다",
                    )
            } catch (e: RestClientException) {
                throw BusinessException.AppleApiException(
                    message = "애플 공개키 API 호출이 실패했습니다",
                    cause = e,
                )
            }

        val matchingKey = keys.keys.find { it.kid == kid }
            ?: throw BusinessException.InvalidAppleTokenException(
                message = "일치하는 애플 공개키를 찾을 수 없습니다 (kid: $kid)",
            )

        val n = BigInteger(1, Base64.getUrlDecoder().decode(matchingKey.n))
        val e = BigInteger(1, Base64.getUrlDecoder().decode(matchingKey.e))
        return KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
    }

    private fun parseJwtHeader(token: String): Map<*, *> {
        val headerPart = token.split(".").firstOrNull()
            ?: throw BusinessException.InvalidAppleTokenException(
                message = "애플 id_token 형식이 올바르지 않습니다",
            )
        val decoded = Base64.getUrlDecoder().decode(headerPart)
        return com.fasterxml.jackson.databind.ObjectMapper().readValue(decoded, Map::class.java)
    }

    data class ApplePublicKeyResponse(val keys: List<AppleKey>)
    data class AppleKey(
        val kty: String,
        val kid: String,
        val use: String,
        val alg: String,
        val n: String,
        val e: String
    )
}
