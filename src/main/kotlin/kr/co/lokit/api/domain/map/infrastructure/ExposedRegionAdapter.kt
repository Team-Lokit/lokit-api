package kr.co.lokit.api.domain.map.infrastructure

import kr.co.lokit.api.domain.map.application.port.ClusterPhotoProjection
import kr.co.lokit.api.domain.map.application.port.RegionCatalogPort
import kr.co.lokit.api.domain.map.application.port.RegionPhotoQueryPort
import kr.co.lokit.api.domain.map.application.port.RegionResolverPort
import kr.co.lokit.api.domain.map.domain.BBox
import kr.co.lokit.api.domain.map.domain.Region
import kr.co.lokit.api.domain.map.domain.RegionCode
import kr.co.lokit.api.domain.map.domain.RegionLevel
import kr.co.lokit.api.domain.map.domain.RegionPhotoAggregate
import kr.co.lokit.api.infrastructure.exposed.schema.PhotoTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

class ExposedRegionAdapter(
    private val database: Database,
) : RegionCatalogPort,
    RegionResolverPort,
    RegionPhotoQueryPort {
    override fun findAllRegions(): List<Region> =
        transaction(database) {
            executeQuery(RegionSqlTemplates.CATALOG_QUERY, {}) { it.toRegion() }
        }

    override fun resolveEupMyeonDongCode(
        longitude: Double,
        latitude: Double,
    ): String? =
        transaction(database) {
            executeQuery(RegionSqlTemplates.COVERS_QUERY, {
                setDouble(1, longitude)
                setDouble(2, latitude)
            }) { it.getString("code") }.firstOrNull()
                ?: executeQuery(RegionSqlTemplates.NEAREST_QUERY, {
                    setDouble(1, longitude)
                    setDouble(2, latitude)
                    setDouble(3, longitude)
                    setDouble(4, latitude)
                }) { it.getString("code") }.firstOrNull()
        }

    override fun aggregateByRegion(
        level: RegionLevel,
        coupleId: Long,
        albumId: Long?,
    ): List<RegionPhotoAggregate> =
        transaction(database) {
            executeQuery(RegionSqlTemplates.aggregateQuery(albumId), {
                setInt(1, level.codeLength)
                setInt(2, level.codeLength)
                setInt(3, level.codeLength)
                setLong(4, coupleId)
                albumId?.let { setLong(5, it) }
            }) { it.toRegionPhotoAggregate() }
        }

    override fun findPhotosByRegionPrefixes(
        prefixes: List<String>,
        coupleId: Long,
    ): List<ClusterPhotoProjection> {
        if (prefixes.isEmpty()) return emptyList()
        val prefixLength = prefixes.first().length
        require(prefixes.all { it.length == prefixLength }) { "region prefixes must share one length: $prefixes" }
        return transaction(database) {
            executeQuery(RegionSqlTemplates.PREFIX_PHOTOS_QUERY, {
                setInt(1, prefixLength)
                setArray(2, connection.createArrayOf("varchar", prefixes.toTypedArray()))
                setLong(3, coupleId)
            }) { it.toClusterPhotoProjection() }
        }
    }

    private fun ResultSet.toRegion() =
        Region(
            code = RegionCode.of(getString("code")),
            name = getString("name"),
            parentCode = getString("parent_code")?.let { RegionCode.of(it) },
            centerLongitude = getDouble("lon"),
            centerLatitude = getDouble("lat"),
            envelope =
                BBox(
                    west = getDouble("west"),
                    south = getDouble("south"),
                    east = getDouble("east"),
                    north = getDouble("north"),
                ),
        )

    private fun ResultSet.toRegionPhotoAggregate() =
        RegionPhotoAggregate(
            regionCode = getString("code"),
            count = getInt("cnt"),
            photoId = getLong("id"),
            thumbnailUrl = getString("url"),
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

private object RegionSqlTemplates {
    const val REGION_TABLE = "region"

    val CATALOG_QUERY =
        """
        SELECT code, name, parent_code, ST_X(center) AS lon, ST_Y(center) AS lat, west, south, east, north
        FROM $REGION_TABLE
        """.trimIndent()

    val COVERS_QUERY =
        """
        SELECT r.code FROM $REGION_TABLE r
        WHERE r.level = 3 AND ST_Covers(r.geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))
        ORDER BY r.code
        LIMIT 1
        """.trimIndent()

    val NEAREST_QUERY =
        """
        SELECT r.code FROM $REGION_TABLE r
        WHERE r.level = 3
          AND ST_DWithin(r.geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 5000)
        ORDER BY r.geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326), r.code
        LIMIT 1
        """.trimIndent()

    fun aggregateQuery(albumId: Long?) =
        """
        SELECT t.code, t.cnt, t.id, t.url, t.taken_at
        FROM (
            SELECT LEFT(p.region_code, ?) AS code,
                   p.id, p.url, p.taken_at,
                   COUNT(*) OVER (PARTITION BY LEFT(p.region_code, ?)) AS cnt,
                   ROW_NUMBER() OVER (PARTITION BY LEFT(p.region_code, ?) ORDER BY p.taken_at DESC, p.id DESC) AS rn
            FROM ${PhotoTable.tableName} p
            WHERE p.is_deleted = false AND p.region_code IS NOT NULL
              AND p.couple_id = ?
              ${if (albumId != null) "AND p.album_id = ?" else ""}
        ) t
        WHERE t.rn = 1
        """.trimIndent()

    val PREFIX_PHOTOS_QUERY =
        """
        SELECT p.id, p.url, p.taken_at, p.address, ST_X(p.location) AS longitude, ST_Y(p.location) AS latitude
        FROM ${PhotoTable.tableName} p
        WHERE p.is_deleted = false
          AND LEFT(p.region_code, ?) = ANY(?)
          AND p.couple_id = ?
        ORDER BY p.taken_at DESC, p.id DESC
        """.trimIndent()
}
