package kr.co.lokit.api.domain.map.application

import kr.co.lokit.api.domain.map.application.port.RegionCatalogPort
import kr.co.lokit.api.domain.map.domain.RegionCatalog
import org.springframework.stereotype.Component

@Component
class RegionCatalogProvider(
    private val regionCatalogPort: RegionCatalogPort,
) {
    @Volatile
    private var cached: RegionCatalog? = null

    fun get(): RegionCatalog {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val catalog = RegionCatalog(regionCatalogPort.findAllRegions())
            if (!catalog.isEmpty()) {
                cached = catalog
            }
            return catalog
        }
    }
}
