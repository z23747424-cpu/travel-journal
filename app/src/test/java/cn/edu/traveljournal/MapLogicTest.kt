package cn.edu.traveljournal

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MapLogicTest {
    @Test fun mapAllowsLocationsOutsideChinaAndRejectsInvalidNumbers() {
        assertEquals(-33.8688,MapPoint(-33.8688,151.2093).latitude,0.000001)
        assertThrows(IllegalArgumentException::class.java){MapPoint(Double.NaN,120.0)}
        assertThrows(IllegalArgumentException::class.java){MapPoint(30.0,181.0)}
        assertNull(Journal(latitude=30.0).mapPoint())
    }
    @Test fun mapPayloadIncludesOnlyLocatedRecordsAndDoesNotExposeJournalBody() {
        val entry=Journal(id="one",title="<img src=x onerror=alert(1)>",body="private story",place="西湖",latitude=30.2,longitude=120.1)
        val array=JSONArray(mapNotesJson(listOf(entry,Journal(id="old",body="legacy"))))
        assertEquals(1,array.length())
        assertEquals(entry.title,array.getJSONObject(0).getString("title"))
        assertFalse(array.toString().contains("private story"))
        assertFalse(array.getJSONObject(0).has("photos"))
    }
    @Test fun markerSelectionRejectsUnknownUnlocatedAndDuplicateIds() {
        val entries=listOf(Journal(id="one",latitude=30.0,longitude=120.0),Journal(id="old"))
        assertEquals(listOf("one"),mapNoteIds("[\"unknown\",\"old\",\"one\",\"one\",42]",entries))
    }
    @Test fun bridgeRejectsInvalidPointsAndCameraValues() {
        val selected=mutableListOf<MapPoint>()
        val cameras=mutableListOf<Double>()
        val bridge=MapBridge({it()},{},{selected.add(it)},{},{_,z->cameras.add(z)})
        bridge.selectPoint(Double.POSITIVE_INFINITY,1.0)
        bridge.selectPoint(91.0,120.0)
        bridge.selectPoint(-33.8,151.2)
        bridge.cameraChanged(30.0,120.0,Double.NaN)
        bridge.cameraChanged(30.0,120.0,25.0)
        bridge.cameraChanged(30.0,120.0,12.0)
        assertEquals(1,selected.size)
        assertEquals(listOf(12.0),cameras)
    }
}
