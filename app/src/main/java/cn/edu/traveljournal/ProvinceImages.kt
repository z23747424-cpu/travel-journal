package cn.edu.traveljournal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val PROVINCE_PHOTO_BUDGET=12*1024*1024
internal const val PROVINCE_ICON_BUDGET=2*1024*1024
internal data class ProvinceImageRequests(val photos:Map<String,List<String>>,val icons:Map<String,String>,val photoEdge:Int,val iconEdge:Int)
internal fun provinceImageRequests(designs:List<ProvinceDesign>,visited:Set<String>,marked:Set<String>):ProvinceImageRequests {
    val photos=designs.filter{it.provinceCode in visited&&it.mode=="photos"}.associate{it.provinceCode to it.photos.take(4)}
    val icons=designs.filter{it.provinceCode in marked&&it.marker.kind=="image"}.mapNotNull{d->d.marker.image?.let{d.provinceCode to it}}.toMap()
    fun edge(budget:Int,count:Int,cap:Int)=sqrt(budget.toDouble()/4/count.coerceAtLeast(1)).toInt().coerceIn(1,cap)
    return ProvinceImageRequests(photos,icons,edge(PROVINCE_PHOTO_BUDGET,photos.values.flatten().distinct().size,512),edge(PROVINCE_ICON_BUDGET,icons.values.distinct().size,192))
}
internal fun provinceBitmapSample(width:Int,height:Int,edge:Int):Int {
    var sample=1
    while(maxOf(width,height).toLong()>edge.toLong()*sample&&sample<(1 shl 29))sample*=2
    return sample
}
internal fun decodeProvinceBitmap(file:File,edge:Int):Bitmap?=runCatching {
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth<=0||bounds.outHeight<=0)return@runCatching null
    val raw=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=provinceBitmapSample(bounds.outWidth,bounds.outHeight,edge);inPreferredConfig=Bitmap.Config.ARGB_8888})?:return@runCatching null
    val orientation=runCatching{ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)}}
    if(orientation==1)raw else Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true).also{if(it!==raw)raw.recycle()}
}.getOrNull()
internal data class ProvinceBitmaps(val photos:Map<String,List<Bitmap>>,val icons:Map<String,Bitmap>)
internal suspend fun loadProvinceBitmaps(requests:ProvinceImageRequests,file:(String)->File):ProvinceBitmaps {
    val created=mutableListOf<Bitmap>()
    suspend fun load(names:List<String>,edge:Int):Map<String,Bitmap>{val out=mutableMapOf<String,Bitmap>();for(name in names.distinct()){
        currentCoroutineContext().ensureActive()
        decodeProvinceBitmap(file(name),edge)?.let{created+=it;out[name]=it}
    };return out}
    try{
        val photos=load(requests.photos.values.flatten(),requests.photoEdge)
        val icons=load(requests.icons.values.toList(),requests.iconEdge)
        currentCoroutineContext().ensureActive()
        return ProvinceBitmaps(requests.photos.mapValues{(_,names)->names.mapNotNull{photos[it]}},requests.icons.mapNotNull{(code,name)->icons[name]?.let{code to it}}.toMap())
    }catch(e:Throwable){created.forEach{it.recycle()};throw e}
}

