package cn.edu.traveljournal

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import kotlin.math.*

internal class NativeMapController {
    internal var view: NativeTravelMapView? = null
    fun showChina() { view?.showChina() }
    fun zoomIn() { view?.changeZoom(1.0) }
    fun zoomOut() { view?.changeZoom(-1.0) }
    fun focus(point:MapPoint) { view?.focus(point) }
    fun focusProvince(province:AtlasProvince) { view?.focusProvince(province) }
    fun chooseCenter() { view?.chooseCenter() }
    fun clearSelection() { view?.clearSelection() }
}

internal data class CityStyle(val journal:Journal,val photos:List<Bitmap>)
internal data class MapMarker(val journal:Journal,val bitmap:Bitmap?)

internal class NativeTravelMapView(context:Context): View(context) {
    var atlas:CityAtlas? = null
    var styles:Map<String,CityStyle> = emptyMap()
    var markers:List<MapMarker> = emptyList()
    var onPoint:(MapPoint)->Unit = {}
    var onNotes:(String)->Unit = {}
    var onCamera:(MapPoint,Double)->Unit = {_,_->}
    var onMove:(String,MapPoint)->Unit = {_,_->}
    var onViewport:()->Unit = {}
    var onProvince:(AtlasProvince?)->Unit = {}
    var focusedProvince:AtlasProvince? = null
        private set
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cityBounds=RectF()
    private val destination=RectF()
    private val source=Rect()
    private val flagPath=Path()
    private var dash=DashPathEffect(floatArrayOf(3f,3f),0f)
    private var center=MapProjection.project(MapPoint(35.0,105.0))
    private var zoom=4.0
    private var dragging:MapMarker?=null
    private var dragPoint:MapPoint?=null
    private var selectedPoint:MapPoint?=null
    private var moved=false
    private var multiTouch=false
    private val density=resources.displayMetrics.density
    init { layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);isClickable=true; isFocusable=true; contentDescription="中国旅行地图，可通过城市列表选择城市" }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);updateDash();post { onViewport() } }
    private fun scale():Double = min(width/70.0,height/60.0).coerceAtLeast(1.0) * 2.0.pow(zoom-4.0)
    private fun updateDash() { val length=3f*density/scale().toFloat();dash=DashPathEffect(floatArrayOf(length,length),0f) }
    private fun screen(p:MapPoint):Pair<Float,Float> { val q=MapProjection.project(p); val s=scale(); return ((q.first-center.first)*s+width/2).toFloat() to ((q.second-center.second)*s+height/2).toFloat() }
    private fun point(x:Float,y:Float):MapPoint { val s=scale(); return MapProjection.unproject(center.first+(x-width/2)/s,center.second+(y-height/2)/s) }
    fun visible(shape:AtlasShape):Boolean {
        if(width==0 || height==0) return true
        val s=scale();return shape.intersects(center.first-width/2/s,center.second-height/2/s,center.first+width/2/s,center.second+height/2/s)
    }
    fun visible(point:MapPoint):Boolean {
        if(width==0 || height==0) return true
        val (x,y)=screen(point);return x>=-48*density && x<=width+48*density && y>=-48*density && y<=height+48*density
    }
    private fun notifyCamera() { onCamera(MapProjection.unproject(center.first,center.second),zoom) }
    fun initialize(p:MapPoint?,z:Double) { if(p!=null) {center=MapProjection.project(p);zoom=z.coerceIn(3.0,12.0);updateDash()} }
    private fun setProvince(province:AtlasProvince?) { focusedProvince=province;onProvince(province) }
    fun restoreProvince(p:MapPoint?) { if(zoom>4.0 && focusedProvince==null && p!=null)atlas?.provinceAt(p)?.let { setProvince(it) } }
    fun showChina() { setProvince(null);center=MapProjection.project(MapPoint(35.0,105.0));zoom=4.0;updateDash();invalidate();notifyCamera() }
    fun changeZoom(delta:Double) { zoom=(zoom+delta).coerceIn(3.0,12.0);if(zoom<=4.0)setProvince(null);updateDash();invalidate();notifyCamera() }
    fun focus(p:MapPoint) { setProvince(atlas?.provinceAt(p));center=MapProjection.project(p);zoom=7.5;updateDash();invalidate();notifyCamera() }
    fun focusProvince(province:AtlasProvince) {
        setProvince(province)
        val b=province.shape.projectedBounds()
        val fit=min(width*.78/(b[2]-b[0]).coerceAtLeast(.01),height*.78/(b[3]-b[1]).coerceAtLeast(.01))
        val base=min(width/70.0,height/60.0).coerceAtLeast(1.0)
        center=(b[0]+b[2])/2 to (b[1]+b[3])/2
        zoom=(4.0+ln(fit/base)/ln(2.0)).coerceIn(4.1,12.0)
        updateDash();invalidate();notifyCamera()
    }
    private fun select(p:MapPoint) { selectedPoint=p;invalidate();onPoint(p) }
    fun chooseCenter() { select(MapProjection.unproject(center.first,center.second)) }
    fun clearSelection() { selectedPoint=null;invalidate() }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(247,245,239))
        val a=atlas ?: return
        val s=scale().toFloat()
        canvas.save();canvas.translate(width/2f,height/2f);canvas.scale(s,s);canvas.translate(-center.first.toFloat(),-center.second.toFloat())
        val focus=focusedProvince
        val showCities=focus!=null || zoom>=6.5 || a.provinceAreas.isEmpty()
        if(focus!=null) {
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(238,242,231);paint.pathEffect=null
            canvas.drawPath(focus.shape.path,paint)
        }
        a.cities.forEach { city ->
            if(!visible(city.shape))return@forEach
            val inFocus=focus==null || city.provinceCode==focus.code
            val alpha=if(inFocus)255 else 55
            val style=styles[city.code]
            if(style!=null) {
                paint.style=Paint.Style.FILL;paint.color=runCatching { Color.parseColor(style.journal.mapFillColor) }.getOrDefault(Color.rgb(169,191,174));paint.pathEffect=null
                paint.alpha=alpha
                canvas.drawPath(city.shape.path,paint)
                if(style.journal.mapFillMode=="photos" && style.photos.isNotEmpty()) {
                    canvas.save();canvas.clipPath(city.shape.path)
                    val b=cityBounds;city.shape.path.computeBounds(b,true)
                    val count=style.photos.size; val columns=if(count==1) 1 else 2; val rows=(count+columns-1)/columns
                    style.photos.forEachIndexed { i, bmp ->
                        val rect=destination
                        if(count==3 && i==2) rect.set(b.left,b.top+b.height()/2,b.right,b.bottom)
                        else rect.set(b.left+b.width()*(i%columns)/columns,b.top+b.height()*(i/columns)/rows,b.left+b.width()*(i%columns+1)/columns,b.top+b.height()*(i/columns+1)/rows)
                        val factor=max(rect.width()/bmp.width,rect.height()/bmp.height);val sw=rect.width()/factor;val sh=rect.height()/factor
                        source.set(((bmp.width-sw)/2).toInt(),((bmp.height-sh)/2).toInt(),((bmp.width+sw)/2).toInt().coerceAtMost(bmp.width),((bmp.height+sh)/2).toInt().coerceAtMost(bmp.height))
                        paint.color=Color.WHITE;paint.alpha=alpha;canvas.drawBitmap(bmp,source,rect,paint)
                    };canvas.restore()
                }
            }
            if(showCities && inFocus) {
                paint.style=Paint.Style.STROKE;paint.color=Color.rgb(172,187,163);paint.strokeWidth=0.65f*density/s
                paint.pathEffect=if(style==null) dash else null
                canvas.drawPath(city.shape.path,paint)
            }
        }
        paint.pathEffect=null;paint.style=Paint.Style.STROKE
        a.provinces.forEach { shape ->
            paint.color=Color.rgb(157,177,148);paint.alpha=if(focus==null || shape===focus.shape)255 else 85
            paint.strokeWidth=(if(shape===focus?.shape)1.4f else .85f)*density/s
            canvas.drawPath(shape.path,paint)
        }
        canvas.restore();paint.alpha=255;paint.pathEffect=null
        drawLabels(canvas,a,showCities)
        markerGroups().forEach { group ->
            val first=group.first();val p=if(first==dragging) dragPoint ?: first.journal.mapPoint()!! else first.journal.mapPoint()!!;val (x,y)=screen(p)
            if(x < -60*density || x > width+60*density || y < -60*density || y > height+60*density) return@forEach
            paint.style=Paint.Style.FILL;paint.alpha=if(focus==null || a.provinceAt(p)?.code==focus.code)255 else 70
            if(group.size>1) {paint.color=Color.rgb(188,118,95);canvas.drawCircle(x,y,13*density,paint);paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=11*density;canvas.drawText(group.size.toString(),x,y+4*density,paint);paint.textAlign=Paint.Align.LEFT}
            else if(first.bitmap!=null) { val size=first.journal.mapStickerSize.coerceIn(24,if(zoom<7.0) 32 else 96)*density;destination.set(x-size/2,y-size/2,x+size/2,y+size/2);paint.color=Color.WHITE;canvas.drawBitmap(first.bitmap,null,destination,paint)
                if(!first.journal.visited){paint.color=Color.rgb(219,160,90);flagPath.reset();flagPath.moveTo(x+size/4,y-size/2);flagPath.lineTo(x+size/4+10*density,y-size/2+4*density);flagPath.lineTo(x+size/4,y-size/2+8*density);flagPath.close();canvas.drawPath(flagPath,paint)}
            }
            else {paint.color=if(first.journal.visited) Color.rgb(174,105,84) else Color.rgb(219,160,90);canvas.drawCircle(x,y,5*density,paint);if(!first.journal.visited){paint.strokeWidth=2*density;canvas.drawLine(x,y,x,y-17*density,paint);flagPath.reset();flagPath.moveTo(x,y-17*density);flagPath.lineTo(x+12*density,y-13*density);flagPath.lineTo(x,y-9*density);flagPath.close();canvas.drawPath(flagPath,paint)}}
        }
        paint.alpha=255
        selectedPoint?.let { selected ->
            val (x,y)=screen(selected);paint.color=Color.rgb(163,91,72);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density
            canvas.drawCircle(x,y,10*density,paint);canvas.drawLine(x-15*density,y,x+15*density,y,paint);canvas.drawLine(x,y-15*density,x,y+15*density,paint)
        }
    }
    private fun drawLabels(canvas:Canvas,a:CityAtlas,showCities:Boolean) {
        paint.style=Paint.Style.FILL;paint.textSize=(if(showCities)11 else 12)*density
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        val placed=mutableListOf<RectF>()
        fun label(text:String,p:MapPoint) {
            val (x,y)=screen(p);val half=paint.measureText(text)/2
            val box=RectF(x-half-3*density,y-13*density,x+half+3*density,y+4*density)
            if(box.left<2*density || box.right>width-2*density || box.top<0 || box.bottom>height || placed.any { RectF.intersects(it,box) })return
            placed+=box
            paint.color=Color.rgb(247,245,239);paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;canvas.drawText(text,x,y,paint)
            paint.color=Color.rgb(83,108,88);paint.style=Paint.Style.FILL;canvas.drawText(text,x,y,paint)
        }
        if(showCities) a.cities.filter { focusedProvince==null || it.provinceCode==focusedProvince?.code }.forEach { label(it.name,it.center) }
        else a.provinceAreas.forEach { label(it.shortName,it.center) }
        paint.textAlign=Paint.Align.LEFT
    }
    private fun markerGroups():List<List<MapMarker>> {
        if(zoom>=7.0) return markers.map { listOf(it) }
        val groups=linkedMapOf<Pair<Int,Int>,MutableList<MapMarker>>()
        markers.forEach { m -> m.journal.mapPoint()?.let { p -> val (x,y)=screen(p);val cell=52*density;groups.getOrPut((x/cell).toInt() to (y/cell).toInt()){mutableListOf()}.add(m) } }
        return groups.values.toList()
    }
    private fun groupHit(group:List<MapMarker>,x:Float,y:Float):Boolean = group.first().journal.mapPoint()?.let { p -> val (sx,sy)=screen(p);hypot((x-sx).toDouble(),(y-sy).toDouble()) <= 24*density } == true
    private fun hit(x:Float,y:Float):List<MapMarker>? = markerGroups().filter { groupHit(it,x,y) }.flatten().takeIf { it.isNotEmpty() }
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent)=true
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean {
            val p=point(e.x,e.y);val a=atlas
            val province=a?.provinceAt(p)
            val group=hit(e.x,e.y)
            if(group!=null)onNotes(JSONArray(group.map { it.journal.id }).toString())
            else if(province!=null && focusedProvince?.code!=province.code)focusProvince(province)
            else a?.cityAt(p)?.let { city ->
                val ids=markers.filter { it.journal.mapPoint()?.let { q -> city.shape.contains(q) } == true }.map { it.journal.id }
                if(ids.isNotEmpty())onNotes(JSONArray(ids).toString()) else select(p)
            }
            performClick();return true
        }
        override fun onDoubleTap(e:MotionEvent):Boolean { zoomAt(1.0,e.x,e.y);notifyCamera();return true }
        override fun onLongPress(e:MotionEvent) { if(multiTouch)return;val group=markerGroups().lastOrNull { groupHit(it,e.x,e.y) };val item=group?.singleOrNull();if(item?.journal?.mapSticker!=null){dragging=item;dragPoint=item.journal.mapPoint();parent?.requestDisallowInterceptTouchEvent(true)}else if(group==null) select(point(e.x,e.y)) }
        override fun onScroll(e1:MotionEvent?,e2:MotionEvent,dx:Float,dy:Float):Boolean { if(dragging!=null){dragPoint=point(e2.x,e2.y);moved=true}else if(!multiTouch){val s=scale();center=(center.first+dx/s) to (center.second+dy/s);moved=true};invalidate();return true }
    })
    private fun zoomAt(delta:Double,x:Float,y:Float) { val before=point(x,y);zoom=(zoom+delta).coerceIn(3.0,12.0);if(zoom<=4.0)setProvince(null);updateDash();val after=point(x,y);val a=MapProjection.project(before);val b=MapProjection.project(after);center=(center.first+a.first-b.first) to (center.second+a.second-b.second);invalidate() }
    private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){ override fun onScale(d:ScaleGestureDetector):Boolean { zoomAt(ln(d.scaleFactor.toDouble())/ln(2.0),d.focusX,d.focusY);moved=true;return true } })
    // GestureDetector calls performClick after confirming a single tap; drags and long presses are not clicks.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN){multiTouch=false;moved=false;parent?.requestDisallowInterceptTouchEvent(true)}
        if(event.pointerCount>1){multiTouch=true;dragging=null;dragPoint=null}
        if(event.actionMasked==MotionEvent.ACTION_MOVE && dragging!=null && !multiTouch){dragPoint=point(event.x,event.y);moved=true;invalidate()}
        scaler.onTouchEvent(event);gestures.onTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP){ val item=dragging;val p=dragPoint;if(item!=null && p!=null && moved && !multiTouch) onMove(item.journal.id,p);dragging=null;dragPoint=null;if(moved)notifyCamera();invalidate() }
        if(event.actionMasked==MotionEvent.ACTION_CANCEL){dragging=null;dragPoint=null;invalidate()}
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}
