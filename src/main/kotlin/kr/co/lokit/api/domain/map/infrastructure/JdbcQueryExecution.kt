package kr.co.lokit.api.domain.map.infrastructure

import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/** 현재 Exposed 트랜잭션의 JDBC 커넥션으로 네이티브 SQL 을 실행한다. 반드시 `transaction {}` 안에서 호출한다. */
internal fun <T> executeQuery(
    sql: String,
    setup: PreparedStatement.() -> Unit,
    mapper: (ResultSet) -> T,
): List<T> {
    val conn = TransactionManager.current().connection.connection as Connection

    return conn.prepareStatement(sql).use { stmt ->
        stmt.setup()
        stmt.executeQuery().use { rs ->
            val results = mutableListOf<T>()
            while (rs.next()) {
                results.add(mapper(rs))
            }
            results
        }
    }
}
