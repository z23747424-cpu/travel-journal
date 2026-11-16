package cn.edu.traveljournal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** 省图层：进入某省后只画该省的区县，并且能命中具体区县。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountyMapRenderTest {
    @Test fun focusedProvinceDrawsItsCountiesAndReturnsToNational() {
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        val units = CountyAtlas.units(context, "110000")
        val dongcheng = units.single { it.code == "110101" }
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
            focusProvince = "110000"
            counties = units
            countyVisited = setOf("110101")
        }
        view.layout(0, 0, 900, 900)
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val point = view.screenPoint(dongcheng.center)
        assertEquals("110101", view.countyAtScreen(point.first, point.second)?.code)
        val painted = bitmap.getPixel(point.first.toInt(), point.second.toInt())
        assertNotEquals(Color.TRANSPARENT, painted)
        // 区县层只覆盖本省：屏幕上的空白角落不能命中任何区县。
        assertNull(view.countyAtScreen(2f, 2f))
        // 回到全国视图后仍是省图，并能重新命中省。
        view.focusProvince = null
        val zhejiang = view.screenPoint(atlas.provinceAreas.single { it.code == "330000" }.center)
        assertEquals("330000", view.provinceAtScreen(zhejiang.first, zhejiang.second)?.code)
        assertNull(view.countyAtScreen(zhejiang.first, zhejiang.second))
    }

    @Test fun countyTapReportsTheCountyUnderTheFinger() {
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        val units = CountyAtlas.units(context, "110000")
        val chaoyang = units.single { it.code == "110105" }
        var tapped: String? = null
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
            focusProvince = "110000"
            counties = units
            onCounty = { tapped = it }
        }
        view.layout(0, 0, 900, 900)
        val point = view.screenPoint(chaoyang.center)
        val time = android.os.SystemClock.uptimeMillis()
        view.onTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, point.first, point.second, 0))
        view.onTouchEvent(android.view.MotionEvent.obtain(time, time + 20, android.view.MotionEvent.ACTION_UP, point.first, point.second, 0))
        assertEquals("110105", tapped)
    }

    @Test fun countyLoadingFrameKeepsTheFocusedProvinceFullSize() {
        // 区县数据是异步读的：数据未到时不能把整张全国图按省图层的比例尺画小。
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        val sichuan = atlas.provinceAreas.single { it.code == "510000" }
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
        }
        view.layout(0, 0, 900, 900)
        view.focusProvince = "510000"
        // 尚未设 counties：这一帧画的必须只有四川，且铺在屏幕相同的位置。
        assertTrue(view.counties.isEmpty())
        val center = view.screenPoint(sichuan.center)
        assertTrue("本省应当居中，实际 ${center.first},${center.second}", abs(center.first - 450f) < 60f && abs(center.second - 450f) < 60f)
        assertEquals("510000", view.provinceAtScreen(450f, 450f)?.code)
        val shanghai = view.screenPoint(atlas.provinceAreas.single { it.code == "310000" }.center)
        assertTrue("外省不应挤在同一屏", shanghai.first < 0f || shanghai.first > 900f || shanghai.second < 0f || shanghai.second > 900f)
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(900 * 900); bitmap.getPixels(pixels, 0, 900, 0, 0, 900, 900)
        assertEquals("省图层底色不该出现外省", 0, pixels.count { it == Color.rgb(238, 236, 226) })
        // 区县到位后换成本省的区县，比例尺不变。
        view.counties = CountyAtlas.units(context, "510000")
        assertEquals("510000", view.provinceAtScreen(450f, 450f)?.code)
    }

    @Test fun zoomingIntoAProvinceAsksTheHostToOpenItsCounties() {
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        var drill: String? = null
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
            onDrillProvince = { drill = it }
        }
        view.layout(0, 0, 900, 900)
        val center = view.provinceAtScreen(450f, 450f)
        assertNotNull("地图中央应当落在陆地上", center)
        repeat(10) { if (drill == null) view.zoomBy(1.4f) }
        assertEquals(center!!.code, drill)
    }

    @Test fun zoomingOutOfTheCountyLayerFallsBackToTheWholeCountry() {
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        val units = CountyAtlas.units(context, "110000")
        var leaves = 0
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
            focusProvince = "110000"
            counties = units
            onLeaveProvince = {
                leaves++
                focusProvince = null
                counties = emptyList()
            }
        }
        view.layout(0, 0, 900, 900)
        repeat(6) { view.zoomBy(1f / 1.4f) }
        assertEquals("缩小到底只退出一次", 1, leaves)
        assertNull(view.focusProvince)
        // 退出后仍是能命中的省图，并且缩得下去直到整张中国地图都在屏内。
        val beijing = atlas.provinceAreas.single { it.code == "110000" }.center
        val bj = view.screenPoint(beijing)
        assertEquals("110000", view.provinceAtScreen(bj.first, bj.second)?.code)
        repeat(8) { view.zoomBy(1f / 1.4f) }
        assertNull("缩到底不该再回到区县", view.countyAtScreen(450f, 450f))
        val center = view.provinceAtScreen(450f, 450f)
        assertNotNull("缩小后地图中央仍是陆地", center)
    }

    @Test fun countyLayerOnlyDrawsTheFocusedProvince() {
        val context = RuntimeEnvironment.getApplication()
        val atlas = CityAtlas.load(context)
        val units = CountyAtlas.units(context, "110000")
        assertTrue("区县数据只属于北京", units.all { it.code.startsWith("11") })
        val view = ProvinceMapView(context).apply {
            this.atlas = atlas
            focusProvince = "110000"
            counties = units
        }
        view.layout(0, 0, 900, 900)
        val bitmap = Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        runCatching {
            val root = java.io.File(System.getProperty("user.dir")).let { if (it.name == "app") it.parentFile else it }
            val dir = java.io.File(root, ".tools/native-map-qa").apply { mkdirs() }
            java.io.File(dir, "county-110000.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val pixels = IntArray(900 * 900); bitmap.getPixels(pixels, 0, 900, 0, 0, 900, 900)
        // 本省底图铺满了屏幕，其它省份没有参与绘制。
        assertTrue(pixels.count { it == Color.rgb(245, 243, 234) } > 900 * 900 / 4)
        // 河北、天津方向只画省界底色，不会出现别的省的区县。
        for ((lat, lon) in listOf(38.04 to 114.51, 39.13 to 117.20)) {
            val p = view.screenPoint(MapPoint(lat, lon))
            assertNull("省外不应命中区县", view.countyAtScreen(p.first, p.second))
        }
    }
}