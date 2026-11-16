package cn.edu.traveljournal

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.*
import kotlin.math.*

internal data class ProvinceMapState(val visited:Set<String>,val marked:Set<String>)
internal fun provinceMapState(atlas:CityAtlas,journals:List<Journal>,plans:List<TravelPlan>):ProvinceMapState = ProvinceMapState(
    journals.filter{it.visited}.mapNotNull{journalProvinceCode(it,atlas)}.toSet(),
    journals.filter{!it.visited}.mapNotNull{journalProvinceCode(it,atlas)}.toSet()+plans.filter{it.status=="planned"}.mapNotNull{it.provinceCode})

/**
 * 旅行地图。两级显示：
 *  - 全国层只画省的多边形，一个省就是一个旅行地点；
 *  - 双指放大到某个省几乎铺满屏幕时自动进入省图层（`onDrillProvince`），
 *    只画该省的区县，外省只剩一层极淡的底，别的省的区县一概不画；
 *  - 在省图层继续缩小到极限（`onLeaveProvince`）就退回全国，放缩是一条连续可逆的路。
 *
 * 绘制顺序固定为「填充 → 省界/县界 → 地点名 → 标志」，所以点亮的绿色永远压在文字下面，
 * 不会盖住任何地名或按钮。
 */
internal class ProvinceMapView(context:Context):View(context) {
    var atlas:CityAtlas?=null; set(v){field=v;invalidate()}
    var visited:Set<String> = emptySet(); set(v){field=v;invalidate()}
    var marked:Set<String> = emptySet(); set(v){field=v;invalidate()}
    var designs:Map<String,ProvinceDesign> = emptyMap(); set(v){field=v;invalidate()}
    var photos:Map<String,List<Bitmap>> = emptyMap(); set(v){field=v;invalidate()}
    var markerPhotos:Map<String,Bitmap> = emptyMap(); set(v){field=v;invalidate()}

    /**
     * 省图层：非空时只画这个省的区县，全国其它省份完全不参与绘制。
     * 切换层级时把比例尺换算成新层级里的等价倍数，所以放大进入 / 缩小退出都是连续的，没有突然拉远拉近。
     */
    var focusProvince:String?=null
        set(v){
            if(field==v)return
            snapshotFrame()
            val previous=field
            field=v
            if(wantFit){zoom=1f;panX=0f;panY=0f;wantFit=false;invalidate();return}
            if(width<=0||height<=0){zoom=1f;panX=0f;panY=0f;invalidate();return}
            val before=fitZoom(previous)
            val after=fitZoom(v)
            zoom=when{
                v==null->(zoom*before).coerceIn(minZoom,leaveMaxZoom)
                // 进省至少到「省铺满九成半屏幕」：不能停在进省阈值换算出来的 0.62，
                // 那正好压在省图层「该缩回全国」的边上，下一帧就会被判成退出。
                previous==null->(zoom/after).coerceAtLeast(enterZoom)
                else->zoom*before/after
            }.coerceIn(if(v==null) minZoom else countyMinZoom,maxZoom)
            panX=0f;panY=0f
            // 退回全国时让刚才那个省继续留在十字架下：视角不该甩回全国正中央，
            // 那里恰好是四川，十字架一落上去就又把人吸进四川。
            if(v==null&&previous!=null)atlas?.provinceAreas?.find{it.code==previous}?.let{keep->
                val (x,y)=screenPoint(keep.center);panX=width/2f-x;panY=height/2f-y
            }
            invalidate()
        }
    var counties:List<AtlasCounty> = emptyList(); set(v){field=v;invalidate()}
    var countyVisited:Set<String> = emptySet(); set(v){field=v;invalidate()}
    var countyMarked:Set<String> = emptySet(); set(v){field=v;invalidate()}

    var onProvince:(String)->Unit={};var onLongProvince:(String)->Unit={};var onMarker:(String)->Unit={}
    var onCounty:(String)->Unit={};var onLongCounty:(String)->Unit={};var onDrillProvince:(String)->Unit={}
    /** 在省图层继续缩小到极限时通知宿主退回全国：缩小的手势本身就能一路退回。 */
    var onLeaveProvince:()->Unit={}
    private var zoom=1f; private var panX=0f; private var panY=0f
    /** 同一根手指的一次捏合里最多切换一次层级，免得「进省→立刻缩回全国→再进省」连跳。 */
    private var scaleGestureActive=false
    private var levelChangedInGesture=false
    /** 手势一开始十字架指着的省：中途任何意外都不该把进省目标换掉。 */
    private var lockedProvince:AtlasProvince?=null
    /** 「全国」按钮要求回到铺满屏幕的比例尺，而不是等比换算后的倍数。 */
    private var wantFit=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val density=resources.displayMetrics.density
    private val scaledDensity=resources.displayMetrics.scaledDensity
    private val outlineColor=Color.rgb(112,132,108)
    private val labelColor=Color.rgb(48,69,50)
    private val fallbackFill=Color.rgb(184,203,179)
    private var fadeFrame:Bitmap?=null
    private var fadeAt=0L
    private val fadeRect=RectF()
    private val fadeMs=200L
    private val maxZoom=9f
    private val minZoom=1f
    /** 小省份（北京、上海、天津、港澳）铺满 62% 屏幕的倍数比最大倍数还大：
     *  开启后一到顶就按十字架直接进省，可在设置里关掉。 */
    var enterProvinceAtMaxZoom=true
    /** 省图层可以继续缩小到的下限；越过这里就退回全国，所以缩小是一条连续的路。 */
    private val countyMinZoom=.6f
    private val returnZoom=.68f
    /** 进省时至少要放大到省铺满九成半屏幕，离 `returnZoom` 留出余量。 */
    private val enterZoom=.95f
    /** 从区县退回全国时最多放大到这个倍数：小省（北京、澳门）不会一步跳进省内的细部。 */
    private val leaveMaxZoom=5.5f
    /** 缩放焦点所在省几乎占满屏幕时切到省图层，跳变幅度小于 1.7 倍。 */
    private val drillFillRatio=.62f
    /** 空地也可以点到名的省份，避免在小屏上找不到不认识的省。 */
    private val alwaysLabelled=setOf("650000","540000","630000","510000","530000","150000","230000","330000","440000","710000","460000")
    init { contentDescription="中国省份旅行地图，支持双指缩放和拖动；放大后进入省内看区县，也可通过找省份打开相册" }

    private class Area(val code:String,val label:String,val center:MapPoint,val shape:AtlasShape)
    /** 区县数据还在加载时继续画省图层，避免闪一下空白。 */
    private val countyView:Boolean get()=focusProvince!=null&&counties.isNotEmpty()
    private fun areas():List<Area> {
        if(countyView) return counties.map{Area(it.code,it.name.removeSuffix("区").removeSuffix("县").ifBlank{it.name},it.center,it.shape)}
        val provinces=atlas?.provinceAreas.orEmpty()
        // 区县数据还在路上的那几帧：只铺本省一家。它跟即将出现的区县层共用同一个外包框，
        // 所以比例尺完全一致，不会闪出“缩小的全国地图”。
        val shown=if(focusProvince!=null) provinces.filter{it.code==focusProvince}.ifEmpty{provinces} else provinces
        return shown.map{Area(it.code,it.shortName,it.center,it.shape)}
    }
    private fun visitedCodes():Set<String> = if(countyView) countyVisited else visited
    private fun markedCodes():Set<String> = if(countyView) countyMarked else marked
    private fun designFor(code:String):ProvinceDesign {
        val key=focusProvince?:code
        return designs[key]?:ProvinceDesign(key)
    }

    private fun boundsOf(shapes:List<DoubleArray>):FloatArray? {
        if(shapes.isEmpty())return null
        val l=shapes.minOf{it[0]}.toFloat();val r=shapes.maxOf{it[2]}.toFloat();val t=shapes.minOf{it[1]}.toFloat();val b=shapes.maxOf{it[3]}.toFloat()
        if(r<=l||b<=t)return null
        return floatArrayOf(l,t,r,b)
    }
    /** 全国层铺满屏幕时的比例尺；省图层的缩放阈值以它为基准，换层前后跳变才可控。 */
    private fun nationalScale():Float {
        val box=boundsOf(atlas?.provinceAreas?.map{it.shape.projectedBounds()}.orEmpty())?:return 1f
        return min((width-32*density)/(box[2]-box[0]),(height-48*density)/(box[3]-box[1])).coerceAtLeast(.0001f)
    }
    /** 把某个省铺满屏幕需要的倍数（相对全国层）。 */
    private fun fitZoom(province:AtlasProvince?):Float {
        val shape=province?.shape?:return maxZoom
        val box=boundsOf(listOf(shape.projectedBounds()))?:return maxZoom
        val fit=min((width-32*density)/(box[2]-box[0]),(height-48*density)/(box[3]-box[1]))
        return (fit/nationalScale()).coerceAtLeast(.01f)
    }
    private fun transform():Matrix {
        val box=boundsOf(areas().map{it.shape.projectedBounds()})
        if(box==null)return Matrix()
        val padX=32*density;val padY=48*density
        val scale=min((width-padX)/(box[2]-box[0]),(height-padY)/(box[3]-box[1]))*zoom
        return Matrix().apply{setValues(floatArrayOf(scale,0f,width/2f-(box[0]+box[2])/2*scale+panX,0f,scale,height/2f-(box[1]+box[3])/2*scale+panY,0f,0f,1f))}
    }
    internal fun screenPoint(point:MapPoint):Pair<Float,Float>{val p=MapProjection.project(point);val f=floatArrayOf(p.first.toFloat(),p.second.toFloat());transform().mapPoints(f);return f[0] to f[1]}
    internal fun provinceAtScreen(x:Float,y:Float):AtlasProvince? {val inv=Matrix();if(!transform().invert(inv))return null;val p=floatArrayOf(x,y);inv.mapPoints(p);return atlas?.provinceAt(MapProjection.unproject(p[0].toDouble(),p[1].toDouble()))}
    internal fun countyAtScreen(x:Float,y:Float):AtlasCounty? {val inv=Matrix();if(!transform().invert(inv))return null;val p=floatArrayOf(x,y);inv.mapPoints(p);return countyAt(MapProjection.unproject(p[0].toDouble(),p[1].toDouble()),counties)}

    fun zoomBy(factor:Float)=zoomAt(factor,width/2f,height/2f)
    fun reset(){zoom=1f;panX=0f;panY=0f;invalidate()}
    /** 「全国」按钮：在省图层直接退回全国并重新铺满屏幕。 */
    fun showChina(){
        if(focusProvince==null){reset();return}
        wantFit=true;onLeaveProvince()
    }
    internal fun zoomAt(factor:Float,focusX:Float,focusY:Float){
        val county=focusProvince!=null
        val old=zoom
        zoom=(zoom*factor).coerceIn(if(county)countyMinZoom else minZoom,maxZoom)
        if(zoom==old){
            if(county&&factor<1f){
                // 已经在最小倍数上继续缩小：省图层退出，全国层保持不动。
                leaveProvince()
            }else if(!county&&factor>1f){
                // 已经顶到最大倍数还在放大：倍数不再变化，但十字架指着的省照样进得去。
                drillAtCrosshair()
            }
            return
        }
        val ratio=zoom/old
        panX=(panX+width/2f-focusX)*ratio+focusX-width/2f
        panY=(panY+height/2f-focusY)*ratio+focusY-height/2f
        // 只有“缩小到底”才归位：放大时的微小增量不能被吃掉，否则慢慢捏会一直卡在 1 倍。
        if(!county&&factor<1f&&zoom<=1.01f){zoom=1f;panX=0f;panY=0f}
        invalidate()
        if(county){
            if(zoom<=returnZoom)leaveProvince()
            return
        }
        // 只有“继续放大”才进省；退出时即使倍数还很高也不会被吸回去。
        if(factor<=1f)return
        drillAtCrosshair()
    }
    /** 十字架（视图正中央）指着的省就是唯一的进省目标：手指捏在哪里都不再改变判定。 */
    internal fun crosshairProvince():AtlasProvince? = provinceAtScreen(width/2f,height/2f)
    /** 这个省铺满屏幕约 62% 需要的倍数；小省算出来会比 maxZoom 还大。 */
    internal fun drillThreshold(province:AtlasProvince):Float=(fitZoom(province)*drillFillRatio).coerceAtLeast(1.6f)
    private fun drillAtCrosshair(){
        if(scaleGestureActive&&levelChangedInGesture)return
        val target=(if(scaleGestureActive)lockedProvince else null)?:crosshairProvince()?:return
        // 大省在铺满屏幕六成时进；阈值超过最大倍数的小省靠“到顶就进”这条兜底。
        if(zoom>=drillThreshold(target)||(enterProvinceAtMaxZoom&&zoom>=maxZoom-.001f)){
            if(scaleGestureActive)levelChangedInGesture=true
            onDrillProvince(target.code)
        }
    }
    /** 把某个地理点挪到十字架正下方。 */
    internal fun centerOn(point:MapPoint){
        if(width==0||height==0)return
        val (x,y)=screenPoint(point)
        panX+=width/2f-x;panY+=height/2f-y;invalidate()
    }
    private fun fitZoom(code:String?):Float = fitZoom(code?.let{c->atlas?.provinceAreas?.find{it.code==c}})
    private fun leaveProvince(){
        if(scaleGestureActive&&levelChangedInGesture)return
        if(scaleGestureActive)levelChangedInGesture=true
        invalidate();onLeaveProvince()
    }

    private fun labelVisible(area:Area):Boolean {
        val codes=markedCodes()
        return area.code in visitedCodes()||area.code in codes||(if(countyView) zoom>=.85f else zoom>=1.75f||area.code in alwaysLabelled)
    }

    override fun onDraw(canvas:Canvas){
        paintFrame(canvas)
        val frame=fadeFrame?:return
        val t=((SystemClock.uptimeMillis()-fadeAt).toFloat()/fadeMs).coerceIn(0f,1f)
        if(t>=1f){frame.recycle();fadeFrame=null;return}
        paint.pathEffect=null;paint.style=Paint.Style.FILL;paint.alpha=((1f-t)*255).toInt()
        fadeRect.set(0f,0f,width.toFloat(),height.toFloat())
        canvas.drawBitmap(frame,null,fadeRect,paint);paint.alpha=255
        postInvalidateOnAnimation()
    }
    /** 层级切换时把上一帧存成位图叠在上面淡出，进出大约 0.2 秒，不依赖 Compose 动画。 */
    private fun snapshotFrame(){
        val sw=width;val sh=height
        if(sw<=0||sh<=0||!isAttachedToWindow)return
        // 半分辨率就够了：它只在 0.2 秒的淡出里用，省一半内存和绘制时间。
        val frame=runCatching{Bitmap.createBitmap(max(1,sw/2),max(1,sh/2),Bitmap.Config.ARGB_8888)}.getOrNull()?:return
        val canvas=Canvas(frame);canvas.scale(frame.width.toFloat()/sw,frame.height.toFloat()/sh)
        paintFrame(canvas)
        fadeFrame?.recycle()
        fadeFrame=frame;fadeAt=SystemClock.uptimeMillis()
    }
    override fun onDetachedFromWindow(){super.onDetachedFromWindow();fadeFrame?.recycle();fadeFrame=null}
    private fun paintFrame(canvas:Canvas){
        val list=areas()
        if(list.isEmpty()||width<=0||height<=0)return
        val matrix=transform()
        val visitedNow=visitedCodes()
        val markedNow=markedCodes()

        // 0. 底图：省图层里外省只留极淡的一层，保证“本省在哪里”看得见，又不会被当成区县。
        if(countyView){
            val provinces=atlas?.provinceAreas.orEmpty()
            paint.style=Paint.Style.FILL;paint.alpha=255
            provinces.forEach{area->
                paint.color=if(area.code==focusProvince) Color.rgb(245,243,234) else Color.rgb(238,236,226)
                val path=Path();area.shape.path.transform(matrix,path);canvas.drawPath(path,paint)
            }
            paint.style=Paint.Style.STROKE;paint.strokeWidth=density*.7f;paint.color=Color.rgb(213,211,199)
            provinces.forEach{area->val path=Path();area.shape.path.transform(matrix,path);canvas.drawPath(path,paint)}
        }

        // 1. 填充：整片地图的最底层，只有点亮的省份/区县上色。
        list.forEach{area->
            if(area.code !in visitedNow)return@forEach
            val path=Path();area.shape.path.transform(matrix,path)
            val design=designFor(area.code)
            paint.style=Paint.Style.FILL;paint.alpha=255
            paint.color=runCatching{Color.parseColor(design.color)}.getOrDefault(fallbackFill)
            canvas.drawPath(path,paint)
            val owner=focusProvince?:area.code
            val images=if(countyView) emptyList() else photos[owner].orEmpty().take(4)
            if(design.mode=="photos"&&images.isNotEmpty()){
                canvas.save();canvas.clipPath(path);val bounds=RectF();path.computeBounds(bounds,true)
                val columns=if(images.size>1)2 else 1;val rows=if(images.size>2)2 else 1
                images.forEachIndexed{i,bm->val cell=RectF(bounds.left+(i%columns)*bounds.width()/columns,bounds.top+(i/columns)*bounds.height()/rows,bounds.left+(i%columns+1)*bounds.width()/columns,bounds.top+(i/columns+1)*bounds.height()/rows);drawCrop(canvas,bm,cell)}
                canvas.restore()
            }
        }

        // 2. 边界：所有省份/区县一起画，后画的边界不会盖掉已点亮的颜色以外的东西。
        paint.style=Paint.Style.STROKE;paint.strokeWidth=density*.9f;paint.color=outlineColor;paint.alpha=255
        list.forEach{area->val path=Path();area.shape.path.transform(matrix,path);canvas.drawPath(path,paint)}

        // 3. 地点名：在填充与边界之后画，保证文字始终可见。
        val labelBoxes=mutableListOf<RectF>()
        val candidates=list.filter{labelVisible(it)}.sortedByDescending{it.shape.area}
        val textSize=(if(focusProvince!=null) 10f else 12f)*scaledDensity
        candidates.forEach{area->
            val p=screenPoint(area.center)
            paint.style=Paint.Style.FILL;paint.color=labelColor;paint.textAlign=Paint.Align.CENTER;paint.textSize=textSize
            val half=paint.measureText(area.label)/2+3*density
            val box=RectF(p.first-half,p.second-paint.textSize,p.first+half,p.second+3*density)
            if(labelBoxes.none{RectF.intersects(it,box)}){
                canvas.drawText(area.label,p.first,p.second,paint)
                labelBoxes+=box
            }
        }

        // 4. 标志：最上层，点一下就能打开计划 / 省装饰。
        list.filter{it.code in markedNow}.forEach{area->drawMarker(canvas,area.code,area.center,designFor(area.code).marker,markerPhotos[area.code]?:focusProvince?.let{markerPhotos[it]})}
    }
    private fun drawCrop(canvas:Canvas,bm:Bitmap,target:RectF){val scale=max(target.width()/bm.width,target.height()/bm.height);val sw=target.width()/scale;val sh=target.height()/scale;val source=Rect(((bm.width-sw)/2).toInt(),((bm.height-sh)/2).toInt(),((bm.width+sw)/2).toInt(),((bm.height+sh)/2).toInt());paint.style=Paint.Style.FILL;paint.alpha=255;canvas.drawBitmap(bm,source,target,paint)}
    private fun drawMarker(canvas:Canvas,code:String,center:MapPoint,m:WishMarker,image:Bitmap?){val (x,y)=screenPoint(center);val s=m.size.coerceIn(16,40)*density;paint.style=Paint.Style.FILL;paint.alpha=255;paint.color=runCatching{Color.parseColor(m.color)}.getOrDefault(Color.rgb(172,120,80))
        if(m.kind=="image"&&image!=null){val scale=min(s/image.width,s/image.height);val w=image.width*scale;val h=image.height*scale;canvas.drawBitmap(image,null,RectF(x-w/2,y-s/2-h/2,x+w/2,y-s/2+h/2),paint);return}
        val path=Path()
        when(m.kind){
            "pin"->{canvas.drawCircle(x,y-s*.65f,s*.3f,paint);path.moveTo(x-s*.23f,y-s*.5f);path.lineTo(x,y);path.lineTo(x+s*.23f,y-s*.5f);path.close();canvas.drawPath(path,paint)}
            "star"->{for(i in 0..9){val a=-PI/2+i*PI/5;val r=if(i%2==0)s*.5 else s*.22;val px=x+cos(a).toFloat()*r.toFloat();val py=y-s*.5f+sin(a).toFloat()*r.toFloat();if(i==0)path.moveTo(px,py)else path.lineTo(px,py)};path.close();canvas.drawPath(path,paint)}
            "heart"->{path.moveTo(x,y);path.cubicTo(x-s,y-s*.7f,x-s*.2f,y-s*1.2f,x,y-s*.7f);path.cubicTo(x+s*.2f,y-s*1.2f,x+s,y-s*.7f,x,y);canvas.drawPath(path,paint)}
            else->{paint.strokeWidth=2*density;canvas.drawLine(x-s*.25f,y,x-s*.25f,y-s,paint);path.moveTo(x-s*.25f,y-s);path.lineTo(x+s*.5f,y-s*.88f);path.lineTo(x+s*.3f,y-s*.42f);path.lineTo(x-s*.25f,y-s*.55f);path.close();canvas.drawPath(path,paint)}
        }
    }
    private fun markerHit(x:Float,y:Float):String? {
        val codes=markedCodes()
        val target=areas().filter{it.code in codes}.minByOrNull{val p=screenPoint(it.center);hypot(p.first-x,p.second-y)}?:return null
        val p=screenPoint(target.center)
        val size=designFor(target.code).marker.size.coerceIn(16,40)*density
        return target.code.takeIf{abs(p.first-x)<=24*density&&abs(p.second-size/2-y)<=24*density}
    }

    /** 两指缩放：始终以十字架为锚，手指落在屏幕哪里都不该把十字架下的省带跑。 */
    private val scaleDetector=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(d:ScaleGestureDetector):Boolean{scaleGestureActive=true;levelChangedInGesture=false;lockedProvince=crosshairProvince();return true}
        override fun onScale(d:ScaleGestureDetector):Boolean{zoomAt(d.scaleFactor,width/2f,height/2f);return true}
        override fun onScaleEnd(d:ScaleGestureDetector){scaleGestureActive=false;levelChangedInGesture=false;lockedProvince=null}
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:android.view.MotionEvent)=true
        override fun onSingleTapUp(e:android.view.MotionEvent):Boolean{
            val flag=markerHit(e.x,e.y)
            if(flag!=null){onMarker(flag);performClick();return true}
            if(focusProvince!=null){countyAtScreen(e.x,e.y)?.let{onCounty(it.code)};performClick();return true}
            provinceAtScreen(e.x,e.y)?.let{onProvince(it.code)};performClick();return true
        }
        override fun onLongPress(e:android.view.MotionEvent){
            if(markerHit(e.x,e.y)!=null)return
            if(focusProvince!=null){countyAtScreen(e.x,e.y)?.let{onLongCounty(it.code)};return}
            provinceAtScreen(e.x,e.y)?.let{onLongProvince(it.code)}
        }
        override fun onScroll(a:android.view.MotionEvent?,b:android.view.MotionEvent,dx:Float,dy:Float):Boolean{if(!scaleDetector.isInProgress){panX=(panX-dx).coerceIn(-width*zoom,width*zoom);panY=(panY-dy).coerceIn(-height*zoom,height*zoom);invalidate()};return true}
    })
    override fun onTouchEvent(event:MotionEvent):Boolean{parent?.requestDisallowInterceptTouchEvent(true);scaleDetector.onTouchEvent(event);gestures.onTouchEvent(event);if(event.actionMasked==MotionEvent.ACTION_UP||event.actionMasked==MotionEvent.ACTION_CANCEL)parent?.requestDisallowInterceptTouchEvent(false);return true}
    override fun performClick():Boolean{super.performClick();return true}
}