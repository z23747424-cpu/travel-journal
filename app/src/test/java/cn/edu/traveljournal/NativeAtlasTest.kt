package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test

class NativeAtlasTest {
    @Test fun cityStylesSurviveJsonAndOldRecordsHaveCompatibleDefaults() {
        val source=journalJson(Journal(title="杭州",body="湖边",latitude=30.2489,longitude=120.1481))
            .put("mapCityCode","330100").put("mapFillColor","#C4A58D").put("mapFillMode","photos")
        val saved=journalJson(journalFromJson(source))
        assertEquals("330100",saved.optString("mapCityCode"))
        assertEquals("#C4A58D",saved.optString("mapFillColor"))
        assertEquals("photos",saved.optString("mapFillMode"))
        source.remove("mapCityCode");source.remove("mapFillColor");source.remove("mapFillMode")
        val legacy=journalJson(journalFromJson(source))
        assertEquals("#A9BFAE",legacy.optString("mapFillColor"))
        assertEquals("color",legacy.optString("mapFillMode"))
        assertTrue(legacy.isNull("mapCityCode"))
    }
    @Test fun invalidCityStyleCannotEnterTheRepository() {
        val source=journalJson(Journal(title="原记录",body="保持完整"))
            .put("mapFillColor","javascript:red").put("mapFillMode","remote")
        assertThrows(IllegalArgumentException::class.java){validateJournal(journalFromJson(source))}
    }
}
