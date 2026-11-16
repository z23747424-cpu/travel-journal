package cn.edu.traveljournal

import android.content.Context
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

internal object MapProjection {
    fun project(p: MapPoint): Pair<Double, Double> = p.longitude to -Math.toDegrees(ln(tan(PI / 4 + Math.toRadians(p.latitude.coerceIn(-85.0,85.0)) / 2)))
    fun unproject(x: Double, y: Double): MapPoint = MapPoint(Math.toDegrees(2 * atan(exp(-Math.toRadians(y))) - PI / 2).coerceIn(-90.0,90.0), x.coerceIn(-180.0,180.0))
}

internal class AtlasShape(val polygons: List<List<List<Pair<Double,Double>>>>) {
    val path = Path().apply {
        fillType = Path.FillType.EVEN_ODD
        polygons.forEach { polygon -> polygon.forEach { ring ->
            ring.forEachIndexed { i, p -> if (i == 0) moveTo(p.first.toFloat(),p.second.toFloat()) else lineTo(p.first.toFloat(),p.second.toFloat()) }
            close()
        } }
    }
    private val bounds = polygons.flatten().flatten().let { points ->
        if (points.isEmpty()) doubleArrayOf(0.0,0.0,0.0,0.0) else doubleArrayOf(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
    }
    fun projectedBounds():DoubleArray = bounds.copyOf()
    // Projected area is used only to make overlap resolution deterministic.
    val area:Double = polygons.sumOf { polygon -> polygon.mapIndexed { index, ring ->
        val signed = ring.indices.sumOf { i -> val a=ring[i]; val b=ring[(i+1)%ring.size]; a.first*b.second-b.first*a.second }
        abs(signed)/2 * if(index==0) 1 else -1
    }.sum().coerceAtLeast(0.0) }
    fun intersects(left:Double,top:Double,right:Double,bottom:Double):Boolean =
        bounds[2]>=left && bounds[0]<=right && bounds[3]>=top && bounds[1]<=bottom
    fun contains(p: MapPoint): Boolean {
        val (x,y) = MapProjection.project(p)
        if (x < bounds[0] || x > bounds[2] || y < bounds[1] || y > bounds[3]) return false
        fun inside(ring: List<Pair<Double,Double>>): Boolean {
            var hit = false
            var j = ring.lastIndex
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[j]
                if ((a.second > y) != (b.second > y) && x < (b.first-a.first)*(y-a.second)/(b.second-a.second)+a.first) hit = !hit
                j = i
            }
            return hit
        }
        // A point on any ring, including a hole edge, belongs to the city.
        val edge = polygons.any { polygon -> polygon.any { ring -> ring.indices.any { i ->
            val a=ring[i];val b=ring[(i+1)%ring.size]
            val cross=(x-a.first)*(b.second-a.second)-(y-a.second)*(b.first-a.first)
            abs(cross)<=1e-9 && x>=min(a.first,b.first)-1e-9 && x<=max(a.first,b.first)+1e-9 && y>=min(a.second,b.second)-1e-9 && y<=max(a.second,b.second)+1e-9
        } } }
        if(edge) return true
        return polygons.any { polygon -> polygon.fold(false) { acc, ring -> acc xor inside(ring) } }
    }
}

internal class AtlasCity(val code:String,val name:String,val provinceCode:String,val provinceName:String,val center:MapPoint,internal val shape:AtlasShape)
internal class AtlasProvince(val code:String,val name:String,val center:MapPoint,val shape:AtlasShape) {
    val shortName:String get() = name.removeSuffix("特别行政区").removeSuffix("维吾尔自治区")
        .removeSuffix("壮族自治区").removeSuffix("回族自治区").removeSuffix("自治区").removeSuffix("省").removeSuffix("市")
}
internal class CityAtlas(val cities:List<AtlasCity>,internal val provinces:List<AtlasShape>,val provinceAreas:List<AtlasProvince> = emptyList()) {
    fun cityAt(point:MapPoint):AtlasCity? = cities.asSequence().filter { it.shape.contains(point) }.minWithOrNull(compareBy<AtlasCity> { it.shape.area }.thenBy { it.code })
    fun provinceAt(point:MapPoint):AtlasProvince? = provinceAreas.asSequence().filter { it.shape.contains(point) }
        .minWithOrNull(compareBy<AtlasProvince> { it.shape.area }.thenBy { it.code })
    fun visitedJournalsByCity(journals:List<Journal>):Map<String,List<Journal>> = journals.asSequence()
        .filter { it.visited }.mapNotNull { journal -> journal.mapPoint()?.let { point -> cityAt(point)?.let { it.code to journal } } }
        .groupBy({ it.first }, { it.second })

    /**
     * 从目的地 / 地区文字猜测省份，用于新建计划时自动填「关联省份」。
     * 判定优先级：完全等于省名或简称 > 文字里出现省名或完整城市名 > 城市名以该文字开头。
     * 猜不出（如海外目的地）返回 null，界面上仍可选「不关联」。
     */
    fun provinceCodeFor(vararg hints:String):String? {
        val texts=hints.mapNotNull { it.trim().takeIf(String::isNotBlank) }
        if(texts.isEmpty())return null
        var best:Pair<Int,String>?=null
        fun consider(kind:Int,matched:Int,code:String){
            if(code.isBlank())return
            val score=kind*1000+matched
            if(best==null||score>best!!.first)best=score to code
        }
        texts.forEach { text ->
            // 县城 / 景区名不在「地级市」索引里，但旅行推荐常直接给这些名字。
            popularCountyProvinces.forEach { (name,code) ->
                when {
                    text==name -> consider(3,name.length,code)
                    text.contains(name) -> consider(2,name.length,code)
                }
            }
            provinceAreas.forEach { province ->
                val aliases=buildList {
                    add(province.name);add(province.shortName)
                    if(province.shortName=="内蒙古")add("内蒙")
                }.filter { it.length>=2 }.distinct()
                aliases.forEach { alias ->
                    when {
                        text==alias -> consider(3,alias.length,province.code)
                        text.contains(alias) -> consider(2,alias.length,province.code)
                    }
                }
            }
            cities.forEach { city ->
                if(city.name.length<2)return@forEach
                when {
                    text==city.name -> consider(3,city.name.length,city.provinceCode)
                    text.contains(city.name) -> consider(2,city.name.length,city.provinceCode)
                    text.length>=2 && city.name.startsWith(text) -> consider(1,text.length,city.provinceCode)
                }
            }
        }
        return best?.second
    }
    companion object {
        @Volatile private var cached: CityAtlas? = null
        fun load(context:Context):CityAtlas = cached ?: synchronized(this) {
            cached ?: context.assets.open("atlas/china-cities.json").bufferedReader().use { parse(it.readText()) }.also { cached = it }
        }
        fun parse(text:String):CityAtlas {
            val root = JSONObject(text)
            require(root.getInt("version") == 1)
            fun shape(geometry:JSONObject):AtlasShape {
                val coordinates=geometry.getJSONArray("coordinates")
                fun ring(a:JSONArray):List<Pair<Double,Double>> = (0 until a.length()).map { i -> a.getJSONArray(i).let { MapProjection.project(MapPoint(it.getDouble(1),it.getDouble(0))) } }
                fun polygon(a:JSONArray):List<List<Pair<Double,Double>>> = (0 until a.length()).map { ring(a.getJSONArray(it)) }
                return AtlasShape(when(geometry.getString("type")) {
                    "Polygon" -> listOf(polygon(coordinates))
                    "MultiPolygon" -> (0 until coordinates.length()).map { polygon(coordinates.getJSONArray(it)) }
                    else -> error("Unsupported geometry")
                })
            }
            val provinceAreas=mutableListOf<AtlasProvince>()
            val provinces = root.getJSONArray("provinces").let { a -> (0 until a.length()).map { i ->
                val feature=a.getJSONObject(i)
                val outline=shape(feature.getJSONObject("geometry"))
                val p=feature.optJSONObject("properties")
                if(p!=null && p.optString("name").isNotBlank()) {
                    val c=p.optJSONArray("centroid") ?: p.optJSONArray("center")
                    if(c!=null) provinceAreas+=AtlasProvince(p.get("adcode").toString(),p.getString("name"),MapPoint(c.getDouble(1),c.getDouble(0)),outline)
                }
                outline
            } }
            val cities = root.getJSONArray("cities").let { a -> (0 until a.length()).map { i ->
                val f=a.getJSONObject(i); val p=f.getJSONObject("properties"); val c=p.getJSONArray("center")
                AtlasCity(p.getString("code"),p.getString("name"),p.getString("provinceCode"),p.getString("provinceName"),MapPoint(c.getDouble(1),c.getDouble(0)),shape(f.getJSONObject("geometry")))
            } }
            return CityAtlas(cities,provinces,provinceAreas)
        }
    }
}
