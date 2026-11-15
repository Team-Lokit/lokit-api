package kr.co.lokit.api.config.notification

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Instant
import kotlin.test.assertEquals

class DiscordNotifierTest {
    private fun notifier(
        version: String = "0123456789abcdef",
        vararg profiles: String,
    ) = DiscordNotifier(
        webhookUrl = "http://localhost/webhook",
        deploymentNotificationEnabled = true,
        shutdownNotificationEnabled = false,
        appVersion = version,
        environment = MockEnvironment().apply { setActiveProfiles(*profiles) },
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.firstEmbed(): Map<String, Any> = (this["embeds"] as List<Map<String, Any>>).first()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.fieldValue(name: String): Any? =
        (this["fields"] as List<Map<String, Any>>).first { it["name"] == name }["value"]

    @Test
    fun `배포 알림에는 프로필과 7자리 커밋 SHA 가 담긴다`() {
        val embed = notifier("0123456789abcdef", "prod").deploymentPayload(Instant.EPOCH).firstEmbed()

        assertEquals("서버 배포 완료 (prod)", embed["title"])
        assertEquals("0123456", embed.fieldValue("Version"))
        assertEquals("prod", embed.fieldValue("Profile"))
        assertEquals("1970-01-01 09:00:00", embed.fieldValue("Timestamp"))
    }

    @Test
    fun `활성 프로필이 없으면 default 로 표시한다`() {
        val embed = notifier("unknown").deploymentPayload(Instant.EPOCH).firstEmbed()

        assertEquals("서버 배포 완료 (default)", embed["title"])
        assertEquals("unknown", embed.fieldValue("Version"))
    }
}
