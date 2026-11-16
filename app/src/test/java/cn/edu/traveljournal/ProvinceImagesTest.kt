package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ProvinceImagesTest {
    @Test fun unusedDesignAssetsAreNeverRequested()=runBlocking {
        val designs=listOf(ProvinceDesign("330000",photos=listOf("color.png"),marker=WishMarker(image="inactive.png")),ProvinceDesign("510000",mode="photos",photos=listOf("unvisited.png"),marker=WishMarker(kind="image",image="unmarked.png")))
        val requests=provinceImageRequests(designs,setOf("330000"),setOf("330000"))
        var calls=0
        val result=loadProvinceBitmaps(requests){calls++;error("must not request unused files")}
        assertEquals(0,calls);assertTrue(result.photos.values.flatten().isEmpty());assertTrue(result.icons.isEmpty())
    }
    @Test fun allThirtyFourFourPhotoDesignsStayWithinAggregateBudget()=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val source=java.io.File(context.cacheDir,"province-large.png")
        val fixture=android.graphics.Bitmap.createBitmap(2048,2048,android.graphics.Bitmap.Config.ARGB_8888)
        source.outputStream().use{fixture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};fixture.recycle()
        val designs=PROVINCE_CODES.map{code->ProvinceDesign(code,mode="photos",photos=(0..3).map{"${code}_$it.png"},marker=WishMarker(kind="image",image="${code}_icon.png"))}
        val requests=provinceImageRequests(designs,PROVINCE_CODES,PROVINCE_CODES)
        assertEquals(16,provinceBitmapSample(2048,2048,requests.photoEdge))
        val result=loadProvinceBitmaps(requests){source}
        assertEquals(136,result.photos.values.sumOf{it.size})
        assertTrue(result.photos.values.flatten().sumOf{it.allocationByteCount.toLong()}<=PROVINCE_PHOTO_BUDGET)
        assertTrue(result.icons.values.sumOf{it.allocationByteCount.toLong()}<=PROVINCE_ICON_BUDGET)
        (result.photos.values.flatten()+result.icons.values).forEach{it.recycle()};source.delete();Unit
    }
    @Test fun sessionCleanupIncludesLateImportsAndPreservesReferencedOnes()=runBlocking {
        val files=mutableSetOf("original.png","saved.png","canceled.png","late.png")
        val referenced=setOf("original.png","saved.png")
        val session=ProvinceImportSession{names->synchronized(files){files.removeAll(names.filter{it !in referenced}.toSet())}}
        session.record("saved.png");session.record("canceled.png")
        session.cleanup(setOf("saved.png")).join()
        assertFalse(files.contains("canceled.png"))
        session.close().join()
        session.record("late.png");session.cleanup().join()
        assertEquals(referenced,files)
    }
}


