package kr.co.lokit.api.domain.user.infrastructure.oauth.apple

import io.jsonwebtoken.Jwts
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Component
@EnableConfigurationProperties(AppleOAuthProperties::class)
class AppleClientSecretGenerator(
    private val properties: AppleOAuthProperties,
) {
    private val privateKey: PrivateKey = loadPrivateKey(properties.privateKeyPath)

    fun generate(): String {
        val now = Instant.now()

        return Jwts.builder()
            .header()
            .keyId(properties.keyId)
            .and()
            .issuer(properties.teamId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(180, ChronoUnit.DAYS)))
            .audience().add("https://appleid.apple.com").and()
            .subject(properties.clientId)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }

    private fun loadPrivateKey(path: String): PrivateKey {
        val pem = Files.readString(Paths.get(path))
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(cleaned)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }
}
