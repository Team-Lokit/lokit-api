package kr.co.lokit.api.domain.map.domain

import java.time.LocalDateTime

enum class RegionLevel(
    val codeLength: Int,
    val clusterIdPrefix: String,
) {
    SIDO(2, "sd"),
    SIGUNGU(5, "sgg"),
    EUPMYEONDONG(8, "emd"),
    ;

    companion object {
        private const val EUPMYEONDONG_MIN_ZOOM = 12.0
        private const val SIGUNGU_MIN_ZOOM = 8.0

        fun forZoom(zoom: Double): RegionLevel? =
            when {
                zoom >= GridValues.CLUSTER_ZOOM_THRESHOLD -> null
                zoom >= EUPMYEONDONG_MIN_ZOOM -> EUPMYEONDONG
                zoom >= SIGUNGU_MIN_ZOOM -> SIGUNGU
                else -> SIDO
            }

        fun ofCodeLength(length: Int): RegionLevel =
            entries.firstOrNull { it.codeLength == length }
                ?: throw IllegalArgumentException("unknown region code length: $length")
    }
}

data class RegionCode private constructor(
    val value: String,
) {
    val level: RegionLevel = RegionLevel.ofCodeLength(value.length)

    fun truncate(level: RegionLevel): RegionCode {
        require(level.codeLength <= value.length) { "cannot truncate $value to $level" }
        return RegionCode(value.substring(0, level.codeLength))
    }

    companion object {
        private val CODE_REGEX = Regex("^(\\d{2}|\\d{5}|\\d{8})$")

        fun of(value: String): RegionCode = requireNotNull(ofOrNull(value)) { "invalid region code: $value" }

        fun ofOrNull(value: String): RegionCode? = if (CODE_REGEX.matches(value)) RegionCode(value) else null
    }
}

data class RegionClusterId(
    val level: RegionLevel,
    val code: RegionCode,
) {
    init {
        require(code.level == level) { "code ${code.value} does not match level $level" }
    }

    fun format(): String = "${level.clusterIdPrefix}_${code.value}"

    companion object {
        fun parse(clusterId: String): RegionClusterId? {
            val level = RegionLevel.entries.firstOrNull { clusterId.startsWith("${it.clusterIdPrefix}_") } ?: return null
            val raw = clusterId.removePrefix("${level.clusterIdPrefix}_")
            val code = RegionCode.ofOrNull(raw)?.takeIf { it.level == level } ?: return null
            return RegionClusterId(level, code)
        }
    }
}

data class Region(
    val code: RegionCode,
    val name: String,
    val parentCode: RegionCode?,
    val centerLongitude: Double,
    val centerLatitude: Double,
    val envelope: BBox,
)

class RegionCatalog(
    regions: Collection<Region>,
) {
    private val byCode: Map<RegionCode, Region> = regions.associateBy { it.code }
    private val childrenByParent: Map<RegionCode, List<RegionCode>> =
        regions.filter { it.parentCode != null }.groupBy({ it.parentCode!! }, { it.code })

    fun find(code: RegionCode): Region? = byCode[code]

    fun isEmpty(): Boolean = byCode.isEmpty()

    fun clusterCodeOf(raw: RegionCode): RegionCode {
        if (raw.level != RegionLevel.SIGUNGU) return raw
        val parent = find(raw)?.parentCode ?: return raw
        return if (parent.level == RegionLevel.SIGUNGU) parent else raw
    }

    fun memberPrefixes(clusterCode: RegionCode): List<RegionCode> {
        if (clusterCode.level != RegionLevel.SIGUNGU) return listOf(clusterCode)
        val subDistricts = childrenByParent[clusterCode].orEmpty().filter { it.level == RegionLevel.SIGUNGU }
        return listOf(clusterCode) + subDistricts
    }

    companion object {
        fun empty(): RegionCatalog = RegionCatalog(emptyList())
    }
}

data class RegionPhotoAggregate(
    val regionCode: String,
    val count: Int,
    val photoId: Long,
    val thumbnailUrl: String,
    val takenAt: LocalDateTime,
)

data class RegionClusterSummary(
    val code: RegionCode,
    val count: Int,
    val photoId: Long,
    val thumbnailUrl: String,
    val takenAt: LocalDateTime,
)

object RegionClusterAssembler {
    fun merge(
        level: RegionLevel,
        aggregates: List<RegionPhotoAggregate>,
        catalog: RegionCatalog,
    ): List<RegionClusterSummary> =
        aggregates
            .mapNotNull { aggregate ->
                val code = RegionCode.ofOrNull(aggregate.regionCode) ?: return@mapNotNull null
                if (code.level != level || catalog.find(code) == null) return@mapNotNull null
                catalog.clusterCodeOf(code) to aggregate
            }.groupBy({ it.first }, { it.second })
            .map { (code, group) ->
                val representative = group.maxWith(compareBy({ it.takenAt }, { it.photoId }))
                RegionClusterSummary(
                    code = code,
                    count = group.sumOf { it.count },
                    photoId = representative.photoId,
                    thumbnailUrl = representative.thumbnailUrl,
                    takenAt = representative.takenAt,
                )
            }

    fun toVisibleClusters(
        summaries: List<RegionClusterSummary>,
        bbox: BBox,
        catalog: RegionCatalog,
    ): Clusters =
        Clusters.of(
            summaries.mapNotNull { summary ->
                val region = catalog.find(summary.code) ?: return@mapNotNull null
                if (!region.envelope.intersects(bbox)) return@mapNotNull null
                ClusterReadModel(
                    clusterId = RegionClusterId(summary.code.level, summary.code).format(),
                    count = summary.count,
                    thumbnailUrl = summary.thumbnailUrl,
                    longitude = region.centerLongitude,
                    latitude = region.centerLatitude,
                    takenAt = summary.takenAt,
                    regionName = region.name,
                )
            },
        )
}
