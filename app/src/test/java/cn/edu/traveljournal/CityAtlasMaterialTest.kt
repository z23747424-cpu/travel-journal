package cn.edu.traveljournal

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CityAtlasMaterialTest {
    @Test fun shippedMaterialHasRealCitiesCorrectCoverageAndVerifiedSourceManifest() {
        val context=RuntimeEnvironment.getApplication()
        val bytes=context.assets.open("atlas/china-cities.json").use{it.readBytes()}
        val content=JSONObject(String(bytes,Charsets.UTF_8))
        val manifest=JSONObject(context.assets.open("atlas/SOURCES.json").bufferedReader().use{it.readText()})
        assertEquals(391,content.getJSONArray("cities").length())
        assertEquals(35,content.getJSONArray("provinces").length())
        assertEquals(391,manifest.getInt("cityUnits"))
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        assertEquals(manifest.getString("sha256"),digest)
        val atlas=CityAtlas.load(context)
        assertEquals(391,atlas.cities.map{it.code}.distinct().size)
        assertEquals(22,atlas.cities.count{it.code.startsWith("TW")})
        assertEquals("330100",atlas.cityAt(MapPoint(30.2489,120.1481))?.code)
        assertEquals("330200",atlas.cityAt(MapPoint(29.8683,121.5440))?.code)
        assertEquals("TW63000",atlas.cityAt(MapPoint(25.0330,121.5654))?.code)
        assertNull(atlas.cityAt(MapPoint(-33.8688,151.2093)))
    }
    @Test fun selectableCityCentersAreInsideTheirOwnBoundary() {
        val atlas=CityAtlas.load(RuntimeEnvironment.getApplication())
        val outside=atlas.cities.filterNot{it.shape.contains(it.center)}.map{it.code+":"+it.name}
        assertTrue("Invalid city selection anchors: $outside",outside.isEmpty())
    }
}
