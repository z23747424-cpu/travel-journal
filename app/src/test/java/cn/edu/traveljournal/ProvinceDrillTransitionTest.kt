package cn.edu.traveljournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「全国 → 省」过渡期的回归：真实手指是慢慢捏的（每帧 O(2%)），
 * 进省那一帧换算出的倍数只有 0.62，正好压在省图层“该缩回全国”的边上，
 * 于是进省→下一帧退出→全国层视角回正（正中央就是四川）→同一根手指顺手进了四川。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProvinceDrillTransitionTest {
    private class Host(val view: ProvinceMapView) {
        var focus: String? = null
        val events = mutableListOf<String>()
        init {
            view.onDrillProvince = { code -> events += "进:$code"; focus = code; view.focusProvince = code }
            view.onLeaveProvince = { events += "退"; focus = null; view.focusProvince = null; view.counties = emptyList() }
        }
    }

    private fun map(atlas: CityAtlas) = ProvinceMapView(RuntimeEnvironment.getApplication()).apply {
        this.atlas = atlas
        layout(0, 0, 1080, 1620)
    }

    /** 一帧一帧地捏，直到进省；返回用的是哪一帧。 */
    private fun pinchUntilEntered(view: ProvinceMapView, host: Host, limit: Int = 400): Int {
        var steps = 0
        while (host.focus == null && steps < limit) { view.zoomBy(1.02f); steps++ }
        return steps
    }

    @Test fun slowPinchStaysInTheProvinceItEntered() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        // 北京/天津靠“到顶就进”，河北/浙江/云南走六成阈值，内蒙古是超大省：四种路径都要覆盖。
        listOf("110000", "120000", "130000", "150000", "330000", "530000", "460000").forEach { code ->
            val province = atlas.provinceAreas.single { it.code == code }
            val view = map(atlas)
            val host = Host(view)
            view.centerOn(province.center)
            assertEquals("十字架应当先落在${province.shortName}", code, view.crosshairProvince()?.code)
            assertTrue("应当能进省", pinchUntilEntered(view, host) < 400)
            assertEquals("进省目标必须是十字架下的省", listOf("进:$code"), host.events)
            // 进省后手指还在继续捏：不许被弹回全国，更不许顺手进四川。
            repeat(5) { view.zoomBy(1.02f) }
            assertEquals("进省后不该再切换层级", listOf("进:$code"), host.events)
            assertEquals(code, host.focus)
        }
    }

    @Test fun countyDataArrivingDoesNotDropBackToTheNation() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val zhejiang = atlas.provinceAreas.single { it.code == "330000" }
        val view = map(atlas)
        val host = Host(view)
        view.centerOn(zhejiang.center)
        pinchUntilEntered(view, host)
        assertEquals(listOf("进:330000"), host.events)
        // 区县数据是进省之后才异步到的：换数据不该重算视角、更不该退出层级。
        view.counties = CountyAtlas.units(RuntimeEnvironment.getApplication(), "330000")
        assertTrue(view.counties.isNotEmpty())
        repeat(4) { view.zoomBy(1.03f) }
        assertEquals(listOf("进:330000"), host.events)
        assertEquals("330000", host.focus)
    }

    @Test fun leavingKeepsTheProvinceUnderTheCrosshair() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val yunnan = atlas.provinceAreas.single { it.code == "530000" }
        val view = map(atlas)
        val host = Host(view)
        view.centerOn(yunnan.center)
        pinchUntilEntered(view, host)
        assertEquals("530000", host.focus)
        // 缩到极限退回全国：视角应当留在云南，而不是甩回全国正中央的四川。
        repeat(40) { if (host.focus != null) view.zoomBy(1f / 1.05f) }
        assertNull("缩到底应当退回全国", host.focus)
        assertEquals("退回全国后十字架该还指着云南", "530000", view.crosshairProvince()?.code)
        assertTrue("一次缩小只该退出一次", host.events.count { it == "退" } == 1)
    }

    private var clock = 1000L
    private fun ev(action: Int, vararg points: Pair<Float, Float>): android.view.MotionEvent {
        val props = points.mapIndexed { i, _ ->
            android.view.MotionEvent.PointerProperties().apply { id = i; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coords = points.map { p ->
            android.view.MotionEvent.PointerCoords().apply { x = p.first; y = p.second; pressure = 1f; size = 1f }
        }.toTypedArray()
        clock += 16
        return android.view.MotionEvent.obtain(0L, clock, action, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    /** 真双指从 520px 张到 1000px（约 1.9 倍），像人手一下一下地推。 */
    private fun pinchAcross(view: ProvinceMapView, from: Float = 520f, to: Float = 1000f, step: Float = 4f) {
        var gap = from
        val shift = android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT
        view.onTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 540f to 810f))
        view.onTouchEvent(ev(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl shift), 540f to 810f, (540f + gap) to 810f))
        while (gap < to) {
            gap += step
            view.onTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, (540f - gap / 2) to 810f, (540f + gap / 2) to 810f))
        }
        view.onTouchEvent(ev(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl shift), (540f - gap / 2) to 810f, (540f + gap / 2) to 810f))
        view.onTouchEvent(ev(android.view.MotionEvent.ACTION_UP, (540f + gap / 2) to 810f))
    }

    /** 真的双指张、不抬手：一次捏合只能切换一次层级。 */
    @Test fun oneRealPinchChangesLevelOnlyOnce() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val zhejiang = atlas.provinceAreas.single { it.code == "330000" }
        val view = map(atlas)
        val host = Host(view)
        view.centerOn(zhejiang.center)
        var pinches = 0
        while (host.focus == null && pinches < 10) { view.centerOn(zhejiang.center); pinchAcross(view); pinches++ }
        assertEquals("该进的是十字架下的浙江", listOf("进:330000"), host.events)
        // 抬手再捏：已经进了省，不该被弹回全国。
        view.centerOn(zhejiang.center)
        pinchAcross(view)
        assertEquals(listOf("进:330000"), host.events)
        assertEquals("330000", host.focus)
    }

    @Test fun buttonsStillEnterFromTheCrosshair() {
        val atlas = CityAtlas.load(RuntimeEnvironment.getApplication())
        val sichuan = atlas.provinceAreas.single { it.code == "510000" }
        val view = map(atlas)
        val host = Host(view)
        view.centerOn(sichuan.center)
        repeat(20) { if (host.focus == null) view.zoomBy(1.4f) }
        assertEquals(listOf("进:510000"), host.events)
        assertNotNull(view.crosshairProvince())
    }
}