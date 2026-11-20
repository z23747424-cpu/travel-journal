package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ProvinceMapTest {
    @Test fun plannedProvinceNeverCountsAsVisited() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        val state=provinceMapState(atlas,listOf(Journal(provinceCode="330000",visited=false)),listOf(TravelPlan(destination="四川",provinceCode="510000")))
        assertTrue(state.visited.isEmpty())
        assertEquals(setOf("330000","510000"),state.marked)
    }
    @Test fun nativeMapPaintsWholeProvinceAndSelectsItsInterior() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        val view=ProvinceMapView(RuntimeEnvironment.getApplication()).apply { this.atlas=atlas; visited=setOf("330000"); designs=mapOf("330000" to ProvinceDesign("330000",color="#123456")) }
        view.layout(0,0,600,600)
        val bitmap=android.graphics.Bitmap.createBitmap(600,600,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val point=view.screenPoint(MapPoint(28.7,119.8))
        assertEquals("330000",view.provinceAtScreen(point.first,point.second)?.code)
        assertEquals(android.graphics.Color.parseColor("#123456"),bitmap.getPixel(point.first.toInt(),point.second.toInt()))
        assertNull(view.provinceAtScreen(-100f,-100f))
    }
    @Test fun provincePhotoIsClippedAndPlanFlagDoesNotPaintAnEmptyProvince() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        val photo=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
        val view=ProvinceMapView(RuntimeEnvironment.getApplication()).apply{this.atlas=atlas;visited=setOf("330000");marked=setOf("510000");designs=mapOf("330000" to ProvinceDesign("330000",mode="photos"));photos=mapOf("330000" to listOf(photo))}
        view.layout(0,0,800,800)
        val image=android.graphics.Bitmap.createBitmap(800,800,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        val zhejiang=view.screenPoint(MapPoint(28.7,119.8));val sichuan=view.screenPoint(MapPoint(31.5,99.4))
        assertEquals(android.graphics.Color.BLUE,image.getPixel(zhejiang.first.toInt(),zhejiang.second.toInt()))
        assertEquals(android.graphics.Color.TRANSPARENT,image.getPixel(sichuan.first.toInt(),sichuan.second.toInt()))
        assertEquals("510000",view.provinceAtScreen(sichuan.first,sichuan.second)?.code)
    }    @Test fun flagHasFortyEightDpTouchTargetAndOpensCustomization() {
        val context=RuntimeEnvironment.getApplication()
        val atlas=CityAtlas.load(context);var chosen:String?=null
        val view=ProvinceMapView(context).apply{this.atlas=atlas;marked=setOf("510000");onMarker={chosen=it}}
        view.layout(0,0,800,800)
        val center=view.screenPoint(atlas.provinceAreas.first{it.code=="510000"}.center)
        val density=context.resources.displayMetrics.density
        val x=center.first+20*density;val y=center.second-12*density
        val time=android.os.SystemClock.uptimeMillis()
        view.onTouchEvent(android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,x,y,0))
        view.onTouchEvent(android.view.MotionEvent.obtain(time,time+20,android.view.MotionEvent.ACTION_UP,x,y,0))
        assertEquals("510000",chosen)
    }    @Test fun rectangularTransparentMarkerIsContainedWithoutStretching() {
        val context=RuntimeEnvironment.getApplication()
        val atlas=CityAtlas.load(context)
        val icon=android.graphics.Bitmap.createBitmap(80,40,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(icon).drawRect(20f,0f,60f,40f,android.graphics.Paint().apply{color=android.graphics.Color.RED})
        val view=ProvinceMapView(context).apply{this.atlas=atlas;marked=setOf("510000");designs=mapOf("510000" to ProvinceDesign("510000",marker=WishMarker(kind="image",size=40)));markerPhotos=mapOf("510000" to icon)}
        view.layout(0,0,1000,1000)
        val output=android.graphics.Bitmap.createBitmap(1000,1000,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(output))
        val p=view.screenPoint(atlas.provinceAreas.first{it.code=="510000"}.center)
        val d=context.resources.displayMetrics.density
        fun pixel(dx:Float,dy:Float)=output.getPixel((p.first+dx*d).toInt(),(p.second+dy*d).toInt())
        assertEquals(android.graphics.Color.RED,pixel(0f,-20f))
        assertEquals("contain leaves vertical letterbox space",android.graphics.Color.TRANSPARENT,pixel(0f,-37f))
        assertEquals("source alpha remains transparent",android.graphics.Color.TRANSPARENT,pixel(16f,-20f))
    }}






