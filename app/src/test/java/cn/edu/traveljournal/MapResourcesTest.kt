package cn.edu.traveljournal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapResourcesTest {
    @Test fun completeChinaGeometryIsServedFromAndroidAssetsWithoutNetwork() {
        val context:Context=RuntimeEnvironment.getApplication()
        val response=LocalMapResources(context).intercept(Uri.parse("https://appassets.androidplatform.net/assets/map/china-boundaries.js"),"GET",false)!!
        assertEquals("application/javascript",response.mimeType)
        val text=response.data.bufferedReader().use{it.readText()}
        for(name in listOf("台湾省","海南省","香港特别行政区","澳门特别行政区"))assertTrue(text.contains(name))
        val html=context.assets.open("map/index.html").bufferedReader().use{it.readText()}
        assertTrue(html.indexOf("china-boundaries.js")<html.indexOf("src=\"map.js\""))
    }
    @Test fun localIllustrationsPreserveAlphaAndAreBoundedThumbnails() {
        val context:Context=RuntimeEnvironment.getApplication()
        val file=File(context.filesDir,"photos/resource-art.png").apply{parentFile!!.mkdirs()}
        try{
            val source=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.TRANSPARENT);source.setPixel(800,300,Color.RED)
            file.outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)};source.recycle()
            val resource=LocalMapResources(context).intercept(Uri.parse("https://appassets.androidplatform.net/photos/resource-art.png"),"GET",false)!!
            assertEquals("image/png",resource.mimeType)
            val bitmap=BitmapFactory.decodeStream(resource.data)!!
            assertTrue(bitmap.width<=256&&bitmap.height<=256)
            assertTrue(bitmap.hasAlpha())
            assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
            bitmap.recycle()
        }finally{file.delete()}
    }
    @Test fun resourceGateRejectsTraversalAndRemoteScripts() {
        val context:Context=RuntimeEnvironment.getApplication()
        val resources=LocalMapResources(context)
        assertEquals(404,resources.intercept(Uri.parse("https://appassets.androidplatform.net/photos/../secret.xml"),"GET",false)!!.statusCode)
        assertEquals(403,resources.intercept(Uri.parse("https://evil.example/script.js"),"GET",false)!!.statusCode)
        assertEquals(403,resources.intercept(Uri.parse("https://tile.openstreetmap.org/index.html"),"GET",true)!!.statusCode)
        assertNull(resources.intercept(Uri.parse("https://tile.openstreetmap.org/2/1/1.png"),"GET",false))
    }
}
