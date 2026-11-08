package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class NativeMapRenderTest {
    @Test fun geometryRespectsHolesAndMultiplePolygons() {
        val atlas = CityAtlas.parse("""{"version":1,"provinces":[],"cities":[{"properties":{"code":"x","name":"X","provinceCode":"p","provinceName":"P","center":[1,1]},"geometry":{"type":"MultiPolygon","coordinates":[[[[0,0],[4,0],[4,4],[0,4],[0,0]],[[1,1],[3,1],[3,3],[1,3],[1,1]]],[[[10,10],[12,10],[12,12],[10,12],[10,10]]]]}}]}""")
        assertEquals("x", atlas.cityAt(MapPoint(0.5, 0.5))?.code)
        assertNull(atlas.cityAt(MapPoint(2.0, 2.0)))
        assertEquals("x", atlas.cityAt(MapPoint(1.0, 2.0))?.code)
        assertEquals("x", atlas.cityAt(MapPoint(11.0, 11.0))?.code)
    }
    @Test fun overlappingCitiesPreferSmallerAreaThenCode() {
        val atlas=CityAtlas.parse("""{"version":1,"provinces":[],"cities":[{"properties":{"code":"big","name":"B","provinceCode":"p","provinceName":"P","center":[2,2]},"geometry":{"type":"Polygon","coordinates":[[[0,0],[4,0],[4,4],[0,4],[0,0]]]}},{"properties":{"code":"small","name":"S","provinceCode":"p","provinceName":"P","center":[2,2]},"geometry":{"type":"Polygon","coordinates":[[[1,1],[3,1],[3,3],[1,3],[1,1]]]}}]}""")
        assertEquals("small",atlas.cityAt(MapPoint(2.0,2.0))?.code)
    }
    @Test fun projectionRoundTrips() {
        val p = MapPoint(30.274, 120.155)
        val xy = MapProjection.project(p)
        val result = MapProjection.unproject(xy.first, xy.second)
        assertEquals(p.latitude, result.latitude, 0.00001)
        assertEquals(p.longitude, result.longitude, 0.00001)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28,35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class NativeCanvasRenderTest {
    @Test fun realHangzhouAndNingboResolveToDifferentCities() {
        val atlas=CityAtlas.load(org.robolectric.RuntimeEnvironment.getApplication())
        val hangzhou=atlas.cityAt(MapPoint(30.2489,120.1481))
        val ningbo=atlas.cityAt(MapPoint(29.8683,121.5440))
        assertEquals("杭州市",hangzhou?.name)
        assertEquals("宁波市",ningbo?.name)
        assertNotEquals(hangzhou?.code,ningbo?.code)
    }
    @Test fun photoIsClippedToCityShape() {
        val atlas=CityAtlas.parse("""{"version":1,"provinces":[],"cities":[{"properties":{"code":"a","name":"A","provinceCode":"p","provinceName":"P","center":[105,35]},"geometry":{"type":"Polygon","coordinates":[[[100,30],[110,30],[110,40],[100,40],[100,30]]]}}]}""")
        val photo=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val view=NativeTravelMapView(org.robolectric.RuntimeEnvironment.getApplication()).apply {
            this.atlas=atlas;styles=mapOf("a" to CityStyle(Journal(mapFillMode="photos"),listOf(photo)))
            measure(android.view.View.MeasureSpec.makeMeasureSpec(480,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY));layout(0,0,480,800)
        }
        val image=android.graphics.Bitmap.createBitmap(480,800,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(image))
        assertEquals(android.graphics.Color.BLUE,image.getPixel(240,400))
        assertEquals(android.graphics.Color.rgb(247,245,239),image.getPixel(350,400))
    }
    @Test fun measuredPhoneAndTabletRenderOutlinesAndVisitedCity() {
        val atlas=CityAtlas.parse("""{"version":1,"provinces":[],"cities":[{"properties":{"code":"a","name":"A","provinceCode":"p","provinceName":"P","center":[105,35]},"geometry":{"type":"Polygon","coordinates":[[[100,30],[110,30],[110,40],[100,40],[100,30]]]}},{"properties":{"code":"b","name":"B","provinceCode":"p","provinceName":"P","center":[115,35]},"geometry":{"type":"Polygon","coordinates":[[[111,30],[119,30],[119,40],[111,40],[111,30]]]}}]}""")
        val ctx=org.robolectric.RuntimeEnvironment.getApplication()
        val visited=Journal(title="visited",latitude=35.0,longitude=105.0,visited=true,mapFillColor="#C4A58D")
        val planned=Journal(title="planned",latitude=35.0,longitude=115.0,visited=false)
        for ((w,h) in listOf(480 to 800, 1200 to 800)) {
            val view=NativeTravelMapView(ctx).apply {
                this.atlas=atlas
                styles=mapOf("a" to CityStyle(visited,emptyList()))
                markers=listOf(MapMarker(planned,null))
                measure(android.view.View.MeasureSpec.makeMeasureSpec(w,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(h,android.view.View.MeasureSpec.EXACTLY))
                layout(0,0,w,h)
            }
            val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val pixels=IntArray(w*h);bitmap.getPixels(pixels,0,w,0,0,w,h)
            fun sample(point:MapPoint):Int {
                val origin=MapProjection.project(MapPoint(35.0,105.0));val target=MapProjection.project(point)
                val scale=minOf(w/70.0,h/60.0)
                return bitmap.getPixel((w/2+(target.first-origin.first)*scale).toInt(),(h/2+(target.second-origin.second)*scale).toInt())
            }
            assertEquals(android.graphics.Color.rgb(196,165,141),sample(MapPoint(32.0,104.0)))
            assertEquals(android.graphics.Color.rgb(247,245,239),sample(MapPoint(32.0,114.0)))
            assertTrue("visited fill absent",pixels.any { it == android.graphics.Color.rgb(196,165,141) })
            assertTrue("planned flag absent",pixels.any { it == android.graphics.Color.rgb(219,160,90) })
            assertTrue("unvisited background absent",pixels.any { it == android.graphics.Color.rgb(247,245,239) })
            assertTrue("boundary absent",pixels.any { val r=android.graphics.Color.red(it);val g=android.graphics.Color.green(it);val b=android.graphics.Color.blue(it);r in 190..230 && g in 195..235 && b in 180..225 })
            val dir=nativeQaDir();dir.mkdirs()
            java.io.File(dir,"native-view-${w}x${h}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}

private fun nativeQaDir():java.io.File {
    val cwd=java.io.File(System.getProperty("user.dir"))
    val root=if(java.io.File(cwd,"src/main").isDirectory) cwd.parentFile else cwd
    return java.io.File(root,".tools/native-map-qa")
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28,35])
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class NativeMapInteractionTest {
    private fun view():NativeTravelMapView = NativeTravelMapView(org.robolectric.RuntimeEnvironment.getApplication()).apply {
        measure(android.view.View.MeasureSpec.makeMeasureSpec(480,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(800,android.view.View.MeasureSpec.EXACTLY))
        layout(0,0,480,800)
    }
    private fun event(view:NativeTravelMapView,down:Long,action:Int,x:Float,y:Float) {
        val e=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0)
        view.onTouchEvent(e);e.recycle()
    }
    private fun waitLongPress() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(700,java.util.concurrent.TimeUnit.MILLISECONDS)
    private fun withSticker(v:NativeTravelMapView) {
        val note=Journal(id="sticker",latitude=35.0,longitude=105.0,mapSticker="example.png")
        v.markers=listOf(MapMarker(note,null))
    }
    @Test fun longPressBlankSelectsPointAndCenterSelectionCallsBack() {
        val v=view();val selected=mutableListOf<MapPoint>();v.onPoint={selected.add(it)}
        val t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,150f,300f);waitLongPress();event(v,t,android.view.MotionEvent.ACTION_UP,150f,300f)
        assertEquals(1,selected.size);assertNotEquals(MapPoint(35.0,105.0),selected.first())
        v.chooseCenter();assertEquals(2,selected.size);assertEquals(35.0,selected.last().latitude,0.00001)
    }
    @Test fun longPressStickerMoveAndUpCommitsButCancelDoesNot() {
        val v=view();withSticker(v);val moved=mutableListOf<Pair<String,MapPoint>>();v.onMove={id,p->moved.add(id to p)}
        var t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);waitLongPress();event(v,t,android.view.MotionEvent.ACTION_MOVE,280f,420f);event(v,t,android.view.MotionEvent.ACTION_UP,280f,420f)
        assertEquals(1,moved.size);assertEquals("sticker",moved[0].first);assertNotEquals(105.0,moved[0].second.longitude,0.001)
        t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);waitLongPress();event(v,t,android.view.MotionEvent.ACTION_MOVE,290f,420f);event(v,t,android.view.MotionEvent.ACTION_CANCEL,290f,420f)
        assertEquals(1,moved.size)
    }
    @Test fun tappingMarkerOpensItsNote() {
        val v=view();withSticker(v);var result="";v.onNotes={result=it}
        val t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);event(v,t,android.view.MotionEvent.ACTION_UP,240f,400f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(700,java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("sticker",org.json.JSONArray(result).getString(0))
    }
    @Test fun tappingCoincidentNotesAtCityZoomOpensBoth() {
        val v=view();val point=MapPoint(35.0,105.0);v.focus(point)
        v.markers=listOf(MapMarker(Journal(id="first",latitude=35.0,longitude=105.0),null),MapMarker(Journal(id="second",latitude=35.0,longitude=105.0),null))
        var result="";v.onNotes={result=it}
        val t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);event(v,t,android.view.MotionEvent.ACTION_UP,240f,400f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(700,java.util.concurrent.TimeUnit.MILLISECONDS)
        val ids=org.json.JSONArray(result);assertEquals(2,ids.length());assertEquals(setOf("first","second"),(0 until ids.length()).map { ids.getString(it) }.toSet())
    }
    @Test fun longPressCoincidentStickersMovesTopmostAtCityZoomButNotNationalCluster() {
        val v=view();val point=MapPoint(35.0,105.0)
        v.markers=listOf(MapMarker(Journal(id="lower",latitude=35.0,longitude=105.0,mapSticker="lower.png"),null),MapMarker(Journal(id="upper",latitude=35.0,longitude=105.0,mapSticker="upper.png"),null))
        val moves=mutableListOf<String>();v.onMove={id,_->moves.add(id)};v.focus(point)
        var t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);waitLongPress();event(v,t,android.view.MotionEvent.ACTION_MOVE,280f,420f);event(v,t,android.view.MotionEvent.ACTION_UP,280f,420f)
        assertEquals(listOf("upper"),moves)
        v.showChina();t=android.os.SystemClock.uptimeMillis();event(v,t,android.view.MotionEvent.ACTION_DOWN,240f,400f);waitLongPress();event(v,t,android.view.MotionEvent.ACTION_MOVE,280f,420f);event(v,t,android.view.MotionEvent.ACTION_UP,280f,420f)
        assertEquals(listOf("upper"),moves)
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class RealAtlasCanvasTest {
    @Test fun visitedGroupingUsesCoordinatesAndExcludesPlans() {
        val atlas=CityAtlas.load(org.robolectric.RuntimeEnvironment.getApplication())
        val old=Journal(id="old",latitude=30.2489,longitude=120.1481,updatedAt=1,mapFillColor="#112233",mapCityCode="330200")
        val latest=Journal(id="latest",latitude=30.2489,longitude=120.1481,updatedAt=2,mapFillColor="#C4A58D")
        val planned=Journal(id="plan",latitude=29.8683,longitude=121.5440,visited=false)
        val grouped=atlas.visitedJournalsByCity(listOf(old,latest,planned))
        val hangzhou=atlas.cityAt(old.mapPoint()!!)!!;val ningbo=atlas.cityAt(planned.mapPoint()!!)!!
        assertEquals(2,grouped[hangzhou.code]?.size);assertEquals("#C4A58D",grouped.getValue(hangzhou.code).maxBy { it.updatedAt }.mapFillColor)
        assertFalse(grouped.containsKey(ningbo.code))
    }
    @Test fun drawRealAtlasWithExplicitFixturesForNationalAndZhejiangViews() {
        val context=org.robolectric.RuntimeEnvironment.getApplication();val atlas=CityAtlas.load(context)
        val hangzhou=atlas.cityAt(MapPoint(30.2489,120.1481))!!
        val fixture=Journal(id="fixture-visited",latitude=30.2489,longitude=120.1481,mapFillColor="#C4A58D",mapFillMode="photos")
        val planned=Journal(id="fixture-flag",latitude=29.8683,longitude=121.5440,visited=false)
        // Synthetic local image used solely to verify collage drawing; not a user photo.
        val photo=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888)
        val fixtureCanvas=android.graphics.Canvas(photo);fixtureCanvas.drawColor(android.graphics.Color.rgb(122,164,150))
        val p=android.graphics.Paint().apply { color=android.graphics.Color.rgb(232,199,156) };fixtureCanvas.drawCircle(32f,32f,22f,p)
        for ((w,h) in listOf(480 to 800,1200 to 800)) for (region in listOf("china","zhejiang")) {
            val v=NativeTravelMapView(context).apply {
                this.atlas=atlas;styles=mapOf(hangzhou.code to CityStyle(fixture,listOf(photo)))
                markers=listOf(MapMarker(fixture,null),MapMarker(planned,null))
                measure(android.view.View.MeasureSpec.makeMeasureSpec(w,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(h,android.view.View.MeasureSpec.EXACTLY));layout(0,0,w,h)
                if(region=="zhejiang")focus(MapPoint(30.2489,120.1481))
            }
            val image=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(image))
            val pixels=IntArray(w*h);image.getPixels(pixels,0,w,0,0,w,h)
            assertTrue(pixels.any { it != android.graphics.Color.rgb(247,245,239) })
            val dir=nativeQaDir();dir.mkdirs();java.io.File(dir,"real-atlas-fixture-$region-${w}x${h}.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
