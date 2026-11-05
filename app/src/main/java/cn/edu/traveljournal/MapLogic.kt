package cn.edu.traveljournal

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class MapPoint(val latitude: Double, val longitude: Double) {
    init { require(validCoordinates(latitude, longitude)) { "地图位置无效" } }
    fun label(): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)
}

internal fun validCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun Journal.mapPoint(): MapPoint? = if (latitude != null && longitude != null && validCoordinates(latitude, longitude)) MapPoint(latitude, longitude) else null

internal fun mapNotesJson(journals: List<Journal>): String = JSONArray(journals.mapNotNull { journal ->
    journal.mapPoint()?.let { point -> JSONObject().put("id", journal.id).put("title", journal.title)
        .put("place", journal.place).put("latitude", point.latitude).put("longitude", point.longitude) }
}).toString()

internal fun mapNoteIds(text: String, journals: List<Journal>): List<String> {
    require(text.length <= 256_000) { "地图选择内容过大" }
    val array = JSONArray(text)
    require(array.length() <= 10_000) { "请放大地图后再查看" }
    val known = journals.filter { it.mapPoint() != null }.map { it.id }.toSet()
    return (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(known::contains) }.distinct()
}
