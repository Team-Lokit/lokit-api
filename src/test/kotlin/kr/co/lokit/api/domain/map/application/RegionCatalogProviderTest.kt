package kr.co.lokit.api.domain.map.application

import kr.co.lokit.api.domain.map.application.port.RegionCatalogPort
import kr.co.lokit.api.domain.map.domain.BBox
import kr.co.lokit.api.domain.map.domain.Region
import kr.co.lokit.api.domain.map.domain.RegionCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class RegionCatalogProviderTest {
    @Mock
    lateinit var regionCatalogPort: RegionCatalogPort

    lateinit var provider: RegionCatalogProvider

    private val gangnam =
        Region(
            code = RegionCode.of("11680"),
            name = "강남구",
            parentCode = RegionCode.of("11"),
            centerLongitude = 127.05,
            centerLatitude = 37.50,
            envelope = BBox(west = 127.0, south = 37.45, east = 127.1, north = 37.55),
        )

    @BeforeEach
    fun setUp() {
        provider = RegionCatalogProvider(regionCatalogPort)
    }

    @Test
    fun `카탈로그를 한 번만 로드한다`() {
        `when`(regionCatalogPort.findAllRegions()).thenReturn(listOf(gangnam))

        provider.get()
        val catalog = provider.get()

        verify(regionCatalogPort, times(1)).findAllRegions()
        assertNotNull(catalog.find(RegionCode.of("11680")))
    }

    @Test
    fun `빈 결과는 캐시하지 않고 다음 호출에서 다시 로드한다`() {
        `when`(regionCatalogPort.findAllRegions()).thenReturn(emptyList(), listOf(gangnam))

        val first = provider.get()
        val second = provider.get()

        assertTrue(first.isEmpty())
        assertFalse(second.isEmpty())
        verify(regionCatalogPort, times(2)).findAllRegions()
    }
}
