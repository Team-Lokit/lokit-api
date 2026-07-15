package kr.co.lokit.api.domain.map.infrastructure

import kr.co.lokit.api.domain.map.application.port.ClusterPhotoProjection
import kr.co.lokit.api.domain.map.application.port.MapQueryPort
import kr.co.lokit.api.domain.map.application.port.PhotoProjection
import kr.co.lokit.api.infrastructure.exposed.schema.PhotoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class ExposedMapQueryAdapter(
    private val database: Database,
) : MapQueryPort {
    override fun findPhotosWithinBBox(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        coupleId: Long?,
        albumId: Long?,
    ): List<PhotoProjection> =
        transaction(database) {
            executeQuery(MapSqlTemplates.photosQuery(coupleId, albumId), {
                setDouble(1, west)
                setDouble(2, south)
                setDouble(3, east)
                setDouble(4, north)
                bindCommonParams(5, coupleId, albumId)
            }) { it.toPhotoProjection() }
        }

    override fun findPhotosInGridCell(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        coupleId: Long?,
    ): List<ClusterPhotoProjection> =
        transaction(database) {
            executeQuery(MapSqlTemplates.gridCellQuery(coupleId), {
                setDouble(1, west)
                setDouble(2, south)
                setDouble(3, east)
                setDouble(4, north)
                coupleId?.let { setLong(5, it) }
            }) { it.toClusterPhotoProjection() }
        }

    private fun <T> executeQuery(
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

    private fun PreparedStatement.bindCommonParams(
        startIndex: Int,
        coupleId: Long?,
        albumId: Long?,
    ) {
        var idx = startIndex
        coupleId?.let { setLong(idx++, it) }
        albumId?.let { setLong(idx++, it) }
    }

    private fun ResultSet.toPhotoProjection() =
        PhotoProjection(
            id = getLong("id"),
            url = getString("url"),
            longitude = getDouble("longitude"),
            latitude = getDouble("latitude"),
            takenAt = getTimestamp("taken_at").toLocalDateTime(),
        )

    private fun ResultSet.toClusterPhotoProjection() =
        ClusterPhotoProjection(
            id = getLong("id"),
            url = getString("url"),
            longitude = getDouble("longitude"),
            latitude = getDouble("latitude"),
            takenAt = getTimestamp("taken_at").toLocalDateTime(),
            address = getString("address"),
        )
}

private object MapSqlTemplates {
    fun photosQuery(
        coupleId: Long?,
        albumId: Long?,
    ) = """
        SELECT p.id, p.url, p.taken_at, p.address, ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude
        FROM ${PhotoTable.tableName} p
        WHERE p.location && ST_MakeEnvelope(?, ?, ?, ?, 4326) AND p.is_deleted = false
        ${if (coupleId != null) "AND p.couple_id = ?" else ""}
        ${if (albumId != null) "AND p.album_id = ?" else ""}
        ORDER BY p.taken_at DESC
        """.trimIndent()

    fun gridCellQuery(coupleId: Long?) =
        """
        SELECT p.id, p.url, p.taken_at, p.address, ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude
        FROM ${PhotoTable.tableName} p
        WHERE p.location && ST_MakeEnvelope(?, ?, ?, ?, 4326) AND p.is_deleted = false
        ${if (coupleId != null) "AND p.couple_id = ?" else ""}
        ORDER BY p.taken_at DESC
        """.trimIndent()
}
