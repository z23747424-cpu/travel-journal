package cn.edu.traveljournal

import org.json.JSONArray
import org.json.JSONObject

internal fun planJson(p:TravelPlan):JSONObject=JSONObject()
    .put("id",p.id).put("title",p.title).put("destination",p.destination).put("origin",p.origin)
    .put("startDate",p.startDate).put("budget",p.budget).put("summary",p.summary).put("notes",p.notes)
    .put("provinceCode",p.provinceCode?:JSONObject.NULL).put("countyCode",p.countyCode?:JSONObject.NULL).put("countyName",p.countyName).put("status",p.status).put("aiGenerated",p.aiGenerated)
    .put("createdAt",p.createdAt).put("updatedAt",p.updatedAt)
    .put("sources",JSONArray(p.sources.map{s->JSONObject().put("id",s.id).put("title",s.title).put("url",s.url)}))
    .put("days",JSONArray(p.days.map{d->JSONObject().put("id",d.id).put("title",d.title).put("activities",JSONArray(d.activities.map{a->JSONObject().put("id",a.id).put("time",a.time).put("place",a.place).put("description",a.description).put("transport",a.transport).put("sourceIds",JSONArray(a.sourceIds))}))}))

internal fun planFromJson(p:JSONObject):TravelPlan=TravelPlan(
    id=strictString(p,"id"),title=strictString(p,"title",""),destination=strictString(p,"destination",""),origin=strictString(p,"origin",""),
    startDate=strictString(p,"startDate",""),budget=strictString(p,"budget",""),summary=strictString(p,"summary",""),notes=strictString(p,"notes",""),
    provinceCode=nativeNullableString(p,"provinceCode"),countyCode=nativeNullableString(p,"countyCode"),countyName=strictString(p,"countyName",""),status=strictString(p,"status","planned"),aiGenerated=nativeBoolean(p,"aiGenerated",false),
    createdAt=nativeLong(p,"createdAt"),updatedAt=nativeLong(p,"updatedAt"),
    sources=objects(p,"sources",30).map{s->TravelSource(strictString(s,"id"),strictString(s,"title"),strictString(s,"url"))},
    days=objects(p,"days",30).map{d->PlanDay(strictString(d,"id"),strictString(d,"title",""),objects(d,"activities",20).map{a->PlanActivity(strictString(a,"id"),strictString(a,"time",""),strictString(a,"place",""),strictString(a,"description",""),strictString(a,"transport",""),stringList(a,"sourceIds"))})}
)
internal fun provinceDesignJson(d:ProvinceDesign):JSONObject=JSONObject().put("provinceCode",d.provinceCode).put("color",d.color).put("mode",d.mode)
    .put("photos",JSONArray(d.photos)).put("coverIndex",d.coverIndex).put("marker",JSONObject().put("kind",d.marker.kind).put("color",d.marker.color).put("size",d.marker.size).put("image",d.marker.image?:JSONObject.NULL))
internal fun provinceDesignFromJson(d:JSONObject):ProvinceDesign{
    val m=d.getJSONObject("marker")
    return ProvinceDesign(strictString(d,"provinceCode"),strictString(d,"color"),strictString(d,"mode"),stringList(d,"photos"),nativeInt(d,"coverIndex"),
        WishMarker(strictString(m,"kind"),strictString(m,"color"),nativeInt(m,"size"),nativeNullableString(m,"image")))
}
private fun objects(json:JSONObject,key:String,max:Int):List<JSONObject>{val array=json.getJSONArray(key);require(array.length()<=max){"列表过长"};return (0 until array.length()).map{array.getJSONObject(it)}}
private fun nativeNullableString(j:JSONObject,key:String)=if(!j.has(key)||j.isNull(key))null else strictString(j,key)
private fun nativeBoolean(j:JSONObject,key:String,default:Boolean):Boolean{if(!j.has(key))return default;val v=j.get(key);require(v is Boolean){"状态字段无效"};return v}
private fun nativeInt(j:JSONObject,key:String):Int{val v=j.get(key);require(v is Number&&v.toDouble()==v.toInt().toDouble()){"整数字段无效"};return v.toInt()}
private fun nativeLong(j:JSONObject,key:String):Long{val v=j.get(key);require(v is Number&&v !is Double&&v !is Float){"时间字段无效"};return v.toLong()}
