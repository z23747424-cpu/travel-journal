package cn.edu.traveljournal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 区县级的离线几何数据。数据由 scripts/prepare-county-atlas.py 生成，随 APK 打包，
 * 运行时只读本地 assets，不做任何联网下载。
 *
 * 目录结构：`atlas/county-index.json`（全国 2872 个单元的代码/名称/中心点，体积小）+
 * `atlas/counties/{省份代码}.json`（该省每个单元的边界，按需加载）。
 */
internal class AtlasCounty(
    val code: String,
    val name: String,
    val provinceCode: String,
    val provinceName: String,
    val center: MapPoint,
    internal val shape: AtlasShape,
)

/** 只有名称与中心点的轻量条目，用于搜索、计划字段和 AI 推荐自动填写。 */
internal data class CountyRef(
    val code: String,
    val name: String,
    val provinceCode: String,
    val provinceName: String,
    val center: MapPoint,
)

internal data class CountyMapState(val visited: Set<String>, val marked: Set<String>)

internal object CountyAtlas {
    private const val INDEX = "atlas/county-index.json"
    private const val GEOMETRY_CACHE_SIZE = 4

    @Volatile private var indexCache: List<CountyRef>? = null

    // 省图是逐个打开的，只留住最近几省的几何，避免整国边界一直占着内存。
    private val geometryCache = object : LinkedHashMap<String, List<AtlasCounty>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AtlasCounty>>) = size > GEOMETRY_CACHE_SIZE
    }

    fun index(context: Context): List<CountyRef> = indexCache ?: synchronized(this) {
        indexCache ?: runCatching { context.assets.open(INDEX).bufferedReader().use { parseIndex(it.readText()) } }
            .getOrDefault(emptyList()).also { indexCache = it }
    }

    fun units(context: Context, provinceCode: String): List<AtlasCounty> {
        if (provinceCode.isBlank()) return emptyList()
        synchronized(geometryCache) { geometryCache[provinceCode]?.let { return it } }
        val loaded = runCatching {
            context.assets.open("atlas/counties/$provinceCode.json").bufferedReader().use { parseUnits(it.readText()) }
        }.getOrDefault(emptyList())
        synchronized(geometryCache) { geometryCache[provinceCode] = loaded }
        return loaded
    }

    internal fun parseIndex(text: String): List<CountyRef> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "区县索引版本不支持" }
        val units = root.getJSONArray("units")
        // 台湾省部分单位用的是 TW… 代码，计划里的区县只收 6 位国标码，这些不进选择列表。
        return (0 until units.length()).mapNotNull { i ->
            val o = units.getJSONObject(i)
            val code = o.getString("code")
            if (!COUNTY_CODE.matches(code)) return@mapNotNull null
            val c = o.getJSONArray("center")
            CountyRef(code, o.getString("name"), o.getString("provinceCode"), o.getString("provinceName"), MapPoint(c.getDouble(1), c.getDouble(0)))
        }
    }

    internal fun parseUnits(text: String): List<AtlasCounty> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "区县地图版本不支持" }
        val provinceCode = root.getString("provinceCode")
        val provinceName = root.getString("provinceName")
        val units = root.getJSONArray("units")
        return (0 until units.length()).map { i ->
            val feature = units.getJSONObject(i)
            val p = feature.getJSONObject("properties")
            val c = p.getJSONArray("center")
            AtlasCounty(p.getString("code"), p.getString("name"), provinceCode, provinceName,
                MapPoint(c.getDouble(1), c.getDouble(0)), shape(feature.getJSONObject("geometry")))
        }
    }

    private fun shape(geometry: JSONObject): AtlasShape {
        val coordinates = geometry.getJSONArray("coordinates")
        fun ring(a: JSONArray): List<Pair<Double, Double>> = (0 until a.length()).map { i ->
            a.getJSONArray(i).let { MapProjection.project(MapPoint(it.getDouble(1), it.getDouble(0))) }
        }
        fun polygon(a: JSONArray): List<List<Pair<Double, Double>>> = (0 until a.length()).map { ring(a.getJSONArray(it)) }
        return AtlasShape(when (geometry.getString("type")) {
            "Polygon" -> listOf(polygon(coordinates))
            "MultiPolygon" -> (0 until coordinates.length()).map { polygon(coordinates.getJSONArray(it)) }
            else -> error("Unsupported county geometry")
        })
    }
}

internal fun countyAt(point: MapPoint, units: List<AtlasCounty>): AtlasCounty? =
    units.asSequence().filter { it.shape.contains(point) }.minWithOrNull(compareBy<AtlasCounty> { it.shape.area }.thenBy { it.code })

/**
 * 一页手账属于哪个区县：优先用地图坐标，其次按城市 / 地点文字匹配。
 * 匹配不到就不画在区县层，绝不猜一个相邻的县。
 */
internal fun countyOfJournal(journal: Journal, units: List<AtlasCounty>): AtlasCounty? {
    if (units.isEmpty()) return null
    journal.mapPoint()?.let { point -> countyAt(point, units)?.let { return it } }
    val texts = listOf(journal.city, journal.place, journal.title, journal.body.take(120))
        .map { it.trim() }.filter { it.length >= 2 }
    if (texts.isEmpty()) return null
    return units.firstOrNull { unit -> texts.any { it == unit.name } }
        ?: units.filter { unit -> unit.name.length >= 2 && texts.any { it.contains(unit.name) } }.minByOrNull { it.name.length }
}

internal fun countyOfPlan(plan: TravelPlan, units: List<AtlasCounty>): AtlasCounty? {
    if (units.isEmpty()) return null
    plan.countyCode?.let { code -> units.firstOrNull { it.code == code }?.let { return it } }
    val texts = listOf(plan.destination, plan.title).map { it.trim() }.filter { it.length >= 2 }
    if (texts.isEmpty()) return null
    return units.firstOrNull { unit -> texts.any { it == unit.name } }
        ?: units.filter { unit -> unit.name.length >= 2 && texts.any { it.contains(unit.name) } }.minByOrNull { it.name.length }
}

/** 省图上的区县层：只有明确指向某个区县的计划才会在这里插旗。 */
internal fun countyMapState(journals: List<Journal>, plans: List<TravelPlan>, units: List<AtlasCounty>): CountyMapState = CountyMapState(
    journals.filter { it.visited }.mapNotNull { countyOfJournal(it, units)?.code }.toSet(),
    journals.filter { !it.visited }.mapNotNull { countyOfJournal(it, units)?.code }.toSet() +
        plans.filter { it.status == "planned" }.mapNotNull { countyOfPlan(it, units)?.code },
)

/** 从目的地 / 城市文字里找出区县，供新建计划时自动填写。省优先，避免同名县串省。 */
internal fun countyRefForText(index: List<CountyRef>, provinceCode: String?, vararg texts: String): CountyRef? {
    if (index.isEmpty()) return null
    val cleaned = texts.mapNotNull { it.trim().takeIf(String::isNotBlank) }
    if (cleaned.isEmpty()) return null
    var best: Pair<Int, CountyRef>? = null
    cleaned.forEach { text ->
        index.forEach { ref ->
            val inProvince = provinceCode != null && ref.provinceCode == provinceCode
            val kind = when {
                text == ref.name -> 3
                ref.name.length >= 2 && text.contains(ref.name) -> 2
                else -> 0
            }
            if (kind == 0) return@forEach
            val score = (if (inProvince) 1000 else 0) + kind * 100 + ref.name.length
            if (best == null || score > best!!.first) best = score to ref
        }
    }
    return best?.second
}

internal fun countyRefOf(index: List<CountyRef>, code: String?): CountyRef? = code?.let { wanted -> index.firstOrNull { it.code == wanted } }