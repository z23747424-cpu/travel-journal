package cn.edu.traveljournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 「用它开始写计划」时自动带出省份的判定规则。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ProvinceLookupTest {
    private val atlas by lazy { CityAtlas.load(RuntimeEnvironment.getApplication()) }

    @Test fun resolvesCityNames() {
        assertEquals("370000", atlas.provinceCodeFor("青岛"))
        assertEquals("330000", atlas.provinceCodeFor("杭州"))
        assertEquals("530000", atlas.provinceCodeFor("大理"))
        assertEquals("450000", atlas.provinceCodeFor("阳朔"))
        assertEquals("360000", atlas.provinceCodeFor("江西婺源"))
        assertEquals("340000", atlas.provinceCodeFor("宏村"))
    }

    @Test fun resolvesProvinceNamesAndAliases() {
        assertEquals("530000", atlas.provinceCodeFor("云南"))
        assertEquals("530000", atlas.provinceCodeFor("云南 · 大理"))
        assertEquals("150000", atlas.provinceCodeFor("内蒙"))
        assertEquals("650000", atlas.provinceCodeFor("新疆"))
    }

    @Test fun prefersExplicitProvinceOverCityWordplay() {
        assertEquals("310000", atlas.provinceCodeFor("上海东方明珠"))
        assertEquals("630000", atlas.provinceCodeFor("海南藏族自治州"))
    }

    @Test fun leavesOverseasAndNoiseEmpty() {
        assertNull(atlas.provinceCodeFor("京都"))
        assertNull(atlas.provinceCodeFor(""))
        assertNull(atlas.provinceCodeFor("随便走走"))
    }
}