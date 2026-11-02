package kr.co.lokit.api.domain.map.application.port

import kr.co.lokit.api.domain.map.domain.Region

interface RegionCatalogPort {
    fun findAllRegions(): List<Region>
}
