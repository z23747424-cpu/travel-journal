package cn.edu.traveljournal

import java.time.LocalDate
import java.util.UUID
import java.net.URI

data class WishMarker(val kind:String="flag",val color:String="#AC7850",val size:Int=24,val image:String?=null)
data class ProvinceDesign(val provinceCode:String,val color:String="#B8CBB3",val mode:String="color",val photos:List<String> = emptyList(),val coverIndex:Int=0,val marker:WishMarker=WishMarker())
data class TravelSource(val id:String,val title:String,val url:String)
data class PlanActivity(val id:String=UUID.randomUUID().toString(),val time:String="",val place:String="",val description:String="",val transport:String="",val sourceIds:List<String> = emptyList())
data class PlanDay(val id:String=UUID.randomUUID().toString(),val title:String="",val activities:List<PlanActivity> = listOf(PlanActivity()))
data class TravelPlan(
    val id:String=UUID.randomUUID().toString(),val title:String="",val destination:String="",val origin:String="",
    val startDate:String="",val budget:String="",val summary:String="",val notes:String="",val provinceCode:String?=null,
    // 两级地点：省必填（海外可留空），区县选填。区县只决定省图上插在哪一格，不影响省旗。
    val countyCode:String?=null,val countyName:String="",
    val status:String="planned",val days:List<PlanDay> = listOf(PlanDay()),val sources:List<TravelSource> = emptyList(),
    val aiGenerated:Boolean=false,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis()
)

internal val PROVINCE_CODES=setOf("110000","120000","130000","140000","150000","210000","220000","230000","310000","320000","330000","340000","350000","360000","370000","410000","420000","430000","440000","450000","460000","500000","510000","520000","530000","540000","610000","620000","630000","640000","650000","710000","810000","820000")
internal val COLOR_HEX=Regex("#[0-9A-Fa-f]{6}")
internal val COUNTY_CODE=Regex("[0-9]{6}")
internal fun validateProvinceDesign(d:ProvinceDesign){
    require(d.provinceCode in PROVINCE_CODES && COLOR_HEX.matches(d.color) && d.mode in setOf("color","photos")){"省份设计无效"}
    require(d.photos.size<=4&&d.photos.distinct().size==d.photos.size&&d.photos.all{PHOTO_NAME.matches(it)}){"省份照片无效"}
    require(d.coverIndex in 0..maxOf(0,d.photos.lastIndex)){"封面位置无效"}
    require(d.marker.kind in setOf("flag","pin","star","heart","image")&&COLOR_HEX.matches(d.marker.color)&&d.marker.size in 16..40){"标记样式无效"}
    require(d.marker.image==null||PHOTO_NAME.matches(d.marker.image)){"标记图片无效"}
    require(d.marker.kind!="image"||d.marker.image!=null){"先选择一张标记图片"}
}
internal fun validateTravelPlan(p:TravelPlan,draft:Boolean=false){
    require(p.id.isNotBlank()&&p.id.length<=160&&p.destination.length<=160&&(draft||p.destination.isNotBlank())){"请填写目的地"}
    require(p.provinceCode==null||p.provinceCode in PROVINCE_CODES){"请选择有效省份"}
    require(p.countyCode==null||(COUNTY_CODE.matches(p.countyCode)&&p.provinceCode!=null)){"请选择有效的区县"}
    require(p.countyName.length<=60&&(p.countyCode==null||p.countyName.isNotBlank())){"区县名称过长或缺失"}
    require(p.status in setOf("draft","planned")&&p.days.size in 1..30&&p.sources.size<=30){"计划状态或天数无效"}
    require(listOf(p.title,p.origin,p.budget).all{it.length<=200}&&p.summary.length<=20000&&p.notes.length<=20000){"计划文字过长"}
    require(draft||p.startDate.isBlank()||runCatching{LocalDate.parse(p.startDate).toString()==p.startDate}.getOrDefault(false)){"请选择有效的出发日期"}
    require(p.sources.map{it.id}.distinct().size==p.sources.size&&p.sources.all{s->s.id.isNotBlank()&&s.id.length<=160&&s.title.length<=500&&s.url.length<=2000&&runCatching{URI(s.url).let{it.scheme in setOf("https","http")&&!it.host.isNullOrBlank()&&it.userInfo==null}}.getOrDefault(false)}){"计划参考来源无效"}
    val refs=p.sources.map{it.id}.toSet();val ids=mutableSetOf<String>()
    p.days.forEach{d->require(d.id.isNotBlank()&&d.id.length<=160&&ids.add(d.id)&&d.title.length<=200&&d.activities.size<=20){"每日安排无效"}
        d.activities.forEach{a->require(a.id.isNotBlank()&&a.id.length<=160&&ids.add(a.id)&&listOf(a.time,a.place,a.transport).all{it.length<=200}&&a.description.length<=20000&&a.sourceIds.size<=30&&a.sourceIds.all{it in refs}){"行程安排或引用无效"}}
    }
}
internal fun journalProvinceCode(journal:Journal,atlas:CityAtlas):String? = journal.provinceCode?.takeIf{it in PROVINCE_CODES}
    ?: journal.mapCityCode?.let{code->atlas.cities.find{it.code==code}?.provinceCode}
    ?: journal.mapPoint()?.let{atlas.provinceAt(it)?.code}
internal fun journalDisplayTitle(j:Journal):String=j.title.ifBlank{j.body.lineSequence().firstOrNull()?.take(24).orEmpty().ifBlank{j.city.ifBlank{j.place}.ifBlank{"旅途中的一页"}}}
internal fun journalLocationLabel(j:Journal):String=listOf(j.place,j.city).map{it.trim()}.filter{it.isNotBlank()}.distinct().joinToString(" · ").ifBlank{"旅途中的一站"}
