package kr.co.lokit.api.config.notification

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PreDestroy
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
@ConditionalOnProperty(name = ["discord.webhook.url"])
@Profile("!local")
class DiscordNotifier(
    @Value("\${discord.webhook.url}") private val webhookUrl: String,
    @Value("\${discord.lifecycle.deployment-enabled:false}") private val deploymentNotificationEnabled: Boolean,
    @Value("\${discord.lifecycle.shutdown-enabled:false}") private val shutdownNotificationEnabled: Boolean,
    @Value("\${APP_VERSION:unknown}") private val appVersion: String,
    private val environment: Environment,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5_000)
                    setReadTimeout(10_000)
                },
            ).build()

    private val backoffUntil =
        Caffeine
            .newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(200)
            .build<String, Instant>()

    private val backoffStep =
        Caffeine
            .newBuilder()
            .expireAfterWrite(2, TimeUnit.HOURS)
            .maximumSize(200)
            .build<String, Int>()

    private val pendingErrors = ConcurrentLinkedQueue<ErrorSnapshot>()
    private val flushScheduled = AtomicBoolean(false)
    private val deploymentNotified = AtomicBoolean(false)
    private val shutdownNotified = AtomicBoolean(false)

    companion object {
        private const val STACKTRACE_MAX_LENGTH = 800
        private const val BATCH_WINDOW_SECONDS = 10L
        private const val MAX_EMBEDS_PER_MESSAGE = 10
        private const val WEBHOOK_MAX_ATTEMPTS = 3
        private val BACKOFF_DURATIONS =
            listOf(
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                Duration.ofHours(1),
            )
        private val KST_FORMATTER =
            DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Seoul"))
    }

    @PreDestroy
    fun notifyShutdown() {
        if (!shutdownNotificationEnabled) {
            return
        }
        if (!shutdownNotified.compareAndSet(false, true)) {
            return
        }
        try {
            val timestamp = KST_FORMATTER.format(Instant.now())
            val payload =
                mapOf(
                    "embeds" to
                        listOf(
                            mapOf(
                                "title" to "서버 종료",
                                "color" to 0xFFA500,
                                "fields" to
                                    listOf(
                                        mapOf("name" to "Timestamp", "value" to timestamp, "inline" to false),
                                    ),
                            ),
                        ),
                )
            sendWebhookWithRetry(payload, "shutdown")
        } catch (e: Exception) {
            log.warn("Discord 종료 알림 전송 실패: ${e.message}")
        }
    }

    /**
     * 컨테이너가 트래픽을 받을 준비가 된 시점(ApplicationReadyEvent)에 배포 완료를 알린다.
     * `@PostConstruct` 는 빈 초기화 시점이라 웹 서버 기동/헬스체크 전에 불릴 수 있어 쓰지 않는다.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun notifyDeployment() {
        if (!deploymentNotificationEnabled) {
            return
        }
        if (!deploymentNotified.compareAndSet(false, true)) {
            return
        }
        Thread.startVirtualThread {
            try {
                sendWebhookWithRetry(deploymentPayload(Instant.now()), "deployment")
            } catch (e: Exception) {
                log.warn("Discord 배포 알림 전송 실패: ${e.message}")
            }
        }
    }

    internal fun deploymentPayload(now: Instant): Map<String, Any> {
        val profiles = environment.activeProfiles.joinToString(",").ifEmpty { "default" }
        return mapOf(
            "embeds" to
                listOf(
                    mapOf(
                        "title" to "서버 배포 완료 ($profiles)",
                        "color" to 0x00FF00,
                        "fields" to
                            listOf(
                                mapOf("name" to "Version", "value" to appVersion.take(7), "inline" to true),
                                mapOf("name" to "Profile", "value" to profiles, "inline" to true),
                                mapOf("name" to "Timestamp", "value" to KST_FORMATTER.format(now), "inline" to false),
                            ),
                    ),
                ),
        )
    }

    fun notify(
        ex: Exception,
        request: HttpServletRequest,
        trace: String? = null,
    ) {
        val deduplicationKey = "${ex::class.simpleName}:${ex.message.hashCode()}"
        val now = Instant.now()

        val until = backoffUntil.getIfPresent(deduplicationKey)
        if (until != null && now.isBefore(until)) return

        val step = (backoffStep.getIfPresent(deduplicationKey) ?: -1) + 1
        val cappedStep = step.coerceAtMost(BACKOFF_DURATIONS.size - 1)
        backoffStep.put(deduplicationKey, cappedStep)
        backoffUntil.put(deduplicationKey, now.plus(BACKOFF_DURATIONS[cappedStep]))

        pendingErrors.add(
            ErrorSnapshot(
                exceptionClass = ex::class.simpleName ?: "Unknown",
                message = ex.message ?: "No message",
                method = request.method,
                uri = request.requestURI,
                stacktrace = ex.stackTraceToString().take(STACKTRACE_MAX_LENGTH),
                timestamp = KST_FORMATTER.format(now),
                trace = trace,
            ),
        )

        if (flushScheduled.compareAndSet(false, true)) {
            Thread.startVirtualThread {
                try {
                    Thread.sleep(Duration.ofSeconds(BATCH_WINDOW_SECONDS))
                    flush()
                } catch (e: Exception) {
                    log.warn("Discord 알림 전송 실패: ${e.message}")
                } finally {
                    flushScheduled.set(false)
                }
            }
        }
    }

    private fun flush() {
        val batch = mutableListOf<ErrorSnapshot>()
        while (batch.size < MAX_EMBEDS_PER_MESSAGE) {
            batch.add(pendingErrors.poll() ?: break)
        }
        if (batch.isEmpty()) return

        val embeds = batch.map { it.toEmbed() }
        val payload = mapOf("embeds" to embeds)
        runCatching { sendWebhookWithRetry(payload, "error-batch:${batch.size}") }
            .onFailure {
                batch.forEach { pendingErrors.add(it) }
                throw it
            }
    }

    private fun sendWebhookWithRetry(
        payload: Map<String, Any>,
        context: String,
    ) {
        var delayMs = 200L
        var last: Throwable? = null
        repeat(WEBHOOK_MAX_ATTEMPTS) { attempt ->
            try {
                restClient
                    .post()
                    .uri(webhookUrl)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                return
            } catch (e: Exception) {
                last = e
                if (attempt == WEBHOOK_MAX_ATTEMPTS - 1) {
                    throw e
                }
                log.warn(
                    "Discord webhook 재시도: attempt={}/{}, context={}",
                    attempt + 1,
                    WEBHOOK_MAX_ATTEMPTS,
                    context,
                    e,
                )
                Thread.sleep(delayMs)
                delayMs *= 2
            }
        }
        throw last ?: IllegalStateException("Discord webhook 전송 실패: context=$context")
    }

    private data class ErrorSnapshot(
        val exceptionClass: String,
        val message: String,
        val method: String,
        val uri: String,
        val stacktrace: String,
        val timestamp: String,
        val trace: String? = null,
    ) {
        fun toEmbed(): Map<String, Any> {
            val fields =
                mutableListOf(
                    field("Message", message),
                    field("Request", "$method $uri"),
                    field("Timestamp", timestamp),
                    field("Stacktrace", "```\n$stacktrace\n```"),
                )
            if (!trace.isNullOrEmpty()) {
                fields.add(field("Trace", "```\n$trace\n```"))
            }
            return mapOf(
                "title" to "[$exceptionClass]",
                "color" to 0xFF0000,
                "fields" to fields,
            )
        }

        private fun field(
            name: String,
            value: String,
        ) = mapOf("name" to name, "value" to value, "inline" to false)
    }
}
