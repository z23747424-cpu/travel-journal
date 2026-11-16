package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 进省判定：只看屏幕中间那个十字架指着的省，手指捏在哪里都不该改变目标。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProvinceDrillTest {
    private fun view(atlas: CityAtlas) = ProvinceMapView(RuntimeEnvironment.getApplication()).apply {
        this.atlas = atlas
        layout(0, 0, 900, 900)
    }

    @Test fun zoomingWithFingersElsewhereEntersTheProvinceUnderTheCrosshair() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val zhejiang = atlas.provinceAreas.single { it.code == "330000" }
        var drill: String? = null
        val map = view(atlas)
        map.onDrillProvince = { drill = it }
        val (fx, fy) = map.screenPoint(zhejiang.center)
        assertNotEquals("十字架不该一开始就落在浙江", "330000", map.crosshairProvince()?.code)
        // 手指一直捏在浙江上，在屏幕上是不动的；判定却该跟着十字架走。
        repeat(6) { if (drill == null) map.zoomAt(1.6f, fx, fy) }
        val locked = map.crosshairProvince()?.code
        assertNotNull("放大几次后十字架下应当有省", locked)
        assertEquals(locked, drill)
        assertNotEquals("手指在浙江上，但十字架不在浙江，不该进浙江", "330000", drill)
    }

    @Test fun pinchingKeepsTheProvinceUnderTheCrosshair() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val chongqing = atlas.provinceAreas.single { it.code == "500000" }
        val map = view(atlas)
        map.centerOn(chongqing.center)
        assertEquals("500000", map.crosshairProvince()?.code)
        // 十字架以自己为锚缩放：手指捏在屏幕哪里，靶心都不会滑到隔壁的四川去。
        repeat(8) { map.zoomBy(1.5f) }
        assertEquals("500000", map.crosshairProvince()?.code)
    }

    @Test fun reachingMaxZoomEntersTinyProvincesWhenEnabled() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val beijing = atlas.provinceAreas.single { it.code == "110000" }
        val map = view(atlas)
        map.centerOn(beijing.center)
        assertEquals("110000", map.crosshairProvince()?.code)
        assertTrue("北京的 62% 阈值本就高于最大倍数", map.drillThreshold(beijing) > 9f)
        var drill: String? = null
        map.onDrillProvince = { drill = it }
        map.enterProvinceAtMaxZoom = true
        repeat(12) { if (drill == null) map.zoomBy(1.4f) }
        assertEquals("放大到最大应当立刻进北京", "110000", drill)
    }

    @Test fun tinyProvincesWaitForTheSettingWhenItIsOff() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val beijing = atlas.provinceAreas.single { it.code == "110000" }
        val map = view(atlas)
        map.centerOn(beijing.center)
        var drill: String? = null
        map.onDrillProvince = { drill = it }
        map.enterProvinceAtMaxZoom = false
        repeat(12) { map.zoomBy(1.4f) }
        assertNull("关掉设置后小省不该被放大带进去", drill)
    }

    @Test fun pinchingAgainAtMaxZoomStillEnters() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val beijing = atlas.provinceAreas.single { it.code == "110000" }
        val map = view(atlas)
        map.centerOn(beijing.center)
        var drill: String? = null
        map.onDrillProvince = { drill = it }
        map.enterProvinceAtMaxZoom = true
        repeat(12) { if (drill == null) map.zoomBy(1.5f) }
        assertEquals("110000", drill)
        // 倍数已经顶到头，再捏一下不该什么都不发生。
        drill = null
        map.zoomBy(1.2f)
        assertEquals("已在最大倍数时再放大也要进省", "110000", drill)
    }

    @Test fun crosshairOverWaterNeverEnters() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val map = view(atlas)
        map.centerOn(MapPoint(25.0, 126.0))
        assertNull("东海海面上不该有省", map.crosshairProvince())
        var drill: String? = null
        map.onDrillProvince = { drill = it }
        repeat(12) { map.zoomBy(1.4f) }
        assertNull("十字架压在海面上时到最大也不进省", drill)
    }

    @Test fun bigProvincesKeepTheirFillThreshold() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val map = view(atlas)
        val zhejiang = atlas.provinceAreas.single { it.code == "330000" }
        assertTrue("浙江铺满六成屏幕的倍数要落在最大倍数以内", map.drillThreshold(zhejiang) < 9f)
        map.centerOn(zhejiang.center)
        assertEquals("330000", map.crosshairProvince()?.code)
        var drill: String? = null
        map.onDrillProvince = { drill = it }
        map.enterProvinceAtMaxZoom = false
        repeat(12) { if (drill == null) map.zoomBy(1.4f) }
        assertEquals("关掉兜底也不影响六成阈值的进省", "330000", drill)
    }
}