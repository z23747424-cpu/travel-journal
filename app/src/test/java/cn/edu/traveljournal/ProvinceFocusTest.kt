package cn.edu.traveljournal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProvinceFocusTest {
    @Test fun nationalProvinceMetadataAndCityCoordinatesResolve() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        assertEquals(34,atlas.provinceAreas.size)
        assertEquals("浙江",atlas.provinceAt(MapPoint(30.2489,120.1481))?.shortName)
        assertEquals("青海",atlas.provinceAt(MapPoint(36.62,101.78))?.shortName)
        assertEquals("330000",atlas.provinceAt(MapPoint(30.2489,120.1481))?.code)
    }
    @Test fun focusRendersProvinceInsteadOfDarkOverlayAndReturnsToNational() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        for((width,height) in listOf(480 to 800,900 to 700)) {
            val view=NativeTravelMapView(RuntimeEnvironment.getApplication()).apply {
                this.atlas=atlas
                measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                layout(0,0,width,height)
            }
            for(code in listOf("330000","630000","820000")) {
                val province=atlas.provinceAreas.single { it.code==code }
                var notified:AtlasProvince?=null
                view.onProvince={notified=it}
                view.focusProvince(province)
                assertSame(province,view.focusedProvince);assertSame(province,notified)
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val pixels=IntArray(width*height);bitmap.getPixels(pixels,0,width,0,0,width,height)
                assertTrue("Missing selected province fill: $code",pixels.count { it==Color.rgb(238,242,231) }>width*height/500)
                assertTrue("Unexpected dark mask",pixels.count { Color.red(it)<40 && Color.green(it)<40 && Color.blue(it)<40 }<width*height/100)
                val root=java.io.File(System.getProperty("user.dir")).let { if(it.name=="app")it.parentFile else it }
                val dir=java.io.File(root,".tools/native-map-qa").apply { mkdirs() }
                java.io.File(dir,"v14-$code-${width}x$height.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            view.showChina();assertNull(view.focusedProvince)
            view.focus(MapPoint(30.2489,120.1481));assertEquals("330000",view.focusedProvince?.code)
            view.changeZoom(-8.0);assertNull(view.focusedProvince)
        }
    }
}
