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
internal class CityAtlas(val cities:List<AtlasCity>,internal val provinces:List<AtlasShape>) {
    fun cityAt(point:MapPoint):AtlasCity? = cities.asSequence().filter { it.shape.contains(point) }.minWithOrNull(compareBy<AtlasCity> { it.shape.area }.thenBy { it.code })
    fun visitedJournalsByCity(journals:List<Journal>):Map<String,List<Journal>> = journals.asSequence()
        .filter { it.visited }.mapNotNull { journal -> journal.mapPoint()?.let { point -> cityAt(point)?.let { it.code to journal } } }
        .groupBy({ it.first }, { it.second })
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
            val provinces = root.getJSONArray("provinces").let { a -> (0 until a.length()).map { shape(a.getJSONObject(it).getJSONObject("geometry")) } }
            val cities = root.getJSONArray("cities").let { a -> (0 until a.length()).map { i ->
                val f=a.getJSONObject(i); val p=f.getJSONObject("properties"); val c=p.getJSONArray("center")
                AtlasCity(p.getString("code"),p.getString("name"),p.getString("provinceCode"),p.getString("provinceName"),MapPoint(c.getDouble(1),c.getDouble(0)),shape(f.getJSONObject("geometry")))
            } }
            return CityAtlas(cities,provinces)
        }
    }
}
