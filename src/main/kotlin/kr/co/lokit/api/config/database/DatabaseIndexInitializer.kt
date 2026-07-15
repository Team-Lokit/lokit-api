package kr.co.lokit.api.config.database

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.sql.SQLException
import javax.sql.DataSource

@Component
class DatabaseIndexInitializer(
    private val dataSource: DataSource,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        Thread
            .ofVirtual()
            .name("db-index-init")
            .start { applyIndexes() }
    }

    private fun applyIndexes() {
        val statements = loadStatements()
        if (statements.isEmpty()) {
            return
        }

        try {
            dataSource.connection.use { conn ->
                val product = conn.metaData.databaseProductName
                if (!product.contains("PostgreSQL", ignoreCase = true)) {
                    log.info("인덱스 초기화 건너뜀 (PostgreSQL 아님: {})", product)
                    return
                }
                conn.autoCommit = true

                var created = 0
                var skippedOrFailed = 0
                statements.forEach { sql ->
                    try {
                        conn.createStatement().use { it.execute(sql) }
                        created++
                    } catch (e: SQLException) {
                        skippedOrFailed++
                        log.warn("인덱스 적용 실패(계속 진행): {} / {}", sql.take(80), e.message)
                    }
                }
                log.info("인덱스 초기화 완료 (실행 {}건, 실패 {}건)", created, skippedOrFailed)
            }
        } catch (e: SQLException) {
            log.warn("인덱스 초기화 중 커넥션 오류: {}", e.message)
        }
    }

    private fun loadStatements(): List<String> =
        ClassPathResource(INDEX_SCRIPT)
            .inputStream
            .bufferedReader()
            .use { it.readText() }
            .lineSequence()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        private const val INDEX_SCRIPT = "db/indexes.sql"
    }
}
