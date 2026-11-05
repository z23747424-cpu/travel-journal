package cn.edu.traveljournal

import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kept for compatibility with old bridge tests; native map never creates a WebView. */
internal class MapBridge(
    private val dispatch: (() -> Unit) -> Unit,
    private val onReady: () -> Unit,
    private val onPoint: (MapPoint) -> Unit,
    private val onNotes: (String) -> Unit,
    private val onCamera: (MapPoint, Double) -> Unit,
    private val onMove: (String,MapPoint) -> Unit = {_,_->},
) {
    @JavascriptInterface fun ready() = dispatch(onReady)
    @JavascriptInterface fun selectPoint(latitude: Double, longitude: Double) { if (validCoordinates(latitude, longitude)) dispatch { onPoint(MapPoint(latitude, longitude)) } }
    @JavascriptInterface fun openNotes(ids: String) { if (ids.length <= 256_000) dispatch { onNotes(ids) } }
    @JavascriptInterface fun cameraChanged(latitude: Double, longitude: Double, zoom: Double) { if (validCoordinates(latitude, longitude) && zoom.isFinite() && zoom in 2.0..19.0) dispatch { onCamera(MapPoint(latitude, longitude), zoom) } }
    @JavascriptInterface fun moveNote(id:String,latitude:Double,longitude:Double) { if(id.length in 1..128&&validCoordinates(latitude,longitude))dispatch{onMove(id,MapPoint(latitude,longitude))} }
}

private data class NativeMapData(val styles:Map<String,CityStyle>,val markers:List<MapMarker>)

@Composable internal fun MapSurface(
    journals: List<Journal>, modifier: Modifier = Modifier, initialPoint: MapPoint? = null, initialZoom: Double = 13.0,
    onPoint: (MapPoint) -> Unit, onNotes: (String) -> Unit = {}, onCamera: (MapPoint, Double) -> Unit = { _, _ -> },
    chooseCenter: Boolean = false,
    onMove:(String,MapPoint)->Unit={_,_->},redrawKey:Int=0,
    repo:JournalRepository?=null,controller:NativeMapController?=null,onAtlasReady:(CityAtlas)->Unit={},
) {
    val context=LocalContext.current
    val localController=remember { NativeMapController() }
    val activeController=controller ?: localController
    val readyCallback by rememberUpdatedState(onAtlasReady)
    var atlas by remember { mutableStateOf<CityAtlas?>(null) }
    var data by remember { mutableStateOf(NativeMapData(emptyMap(),emptyList())) }
    var loadError by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var viewportRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(context,loadAttempt) {
        loadError=false
        val loaded=withContext(Dispatchers.IO) { runCatching { CityAtlas.load(context) }.getOrNull() }
        atlas=loaded;loadError=loaded==null
        loaded?.let { readyCallback(it) }
    }
    LaunchedEffect(atlas,journals,repo,redrawKey,viewportRevision) {
        val a=atlas ?: return@LaunchedEffect
        val view=activeController.view
        val visibleCities=a.cities.filter { view?.visible(it.shape) != false }.map { it.code }.toSet()
        val visibleNotes=journals.filter { it.mapPoint()?.let { p -> view?.visible(p) != false } == true }.map { it.id }.toSet()
        data=withContext(Dispatchers.IO) {
            val bitmaps=mutableMapOf<String,android.graphics.Bitmap?>()
            fun bitmap(name:String):android.graphics.Bitmap? {
                if(name !in bitmaps && bitmaps.size<64) bitmaps[name]=runCatching {
                    val bytes=repo?.photoFile(name)?.let(::mapThumbnail) ?: return@runCatching null
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                }.getOrNull()
                return bitmaps[name]
            }
            val located=journals.mapNotNull { j -> j.mapPoint()?.let { j to it } }
            val byCity=a.visitedJournalsByCity(journals)
            val styles=byCity.mapValues { (code,notes) ->
                val latest=notes.maxBy { it.updatedAt }
                CityStyle(latest,if(code in visibleCities && latest.mapFillMode=="photos") notes.sortedByDescending { it.updatedAt }.flatMap { it.photos }.distinct().take(4).mapNotNull(::bitmap) else emptyList())
            }
            val markers=located.map { (j,_) -> MapMarker(j,j.mapSticker?.takeIf { j.id in visibleNotes && it in j.photos }?.let(::bitmap)) }
            NativeMapData(styles,markers)
        }
    }
    DisposableEffect(activeController) { onDispose { activeController.view=null } }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory={ ctx -> NativeTravelMapView(ctx).apply { initialize(initialPoint,initialZoom);activeController.view=this } },modifier=Modifier.fillMaxSize(),update={ v ->
                activeController.view=v;v.atlas=atlas;v.styles=data.styles;v.markers=data.markers;v.onPoint=onPoint;v.onNotes=onNotes;v.onCamera={ p,z -> viewportRevision++;onCamera(p,z) };v.onViewport={ viewportRevision++ };v.onMove=onMove;v.invalidate()
            })
            if(atlas==null) Column(Modifier.align(Alignment.Center).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(loadError) { Text("离线地图数据加载失败");TextButton(onClick={loadAttempt++}) { Text("重试") } }
                else { CircularProgressIndicator();Text("正在加载离线地图…",Modifier.padding(top=8.dp)) }
            }
        }
        if(chooseCenter) TextButton(onClick={activeController.chooseCenter()},enabled=atlas!=null,modifier=Modifier.fillMaxWidth()) { Text("选择地图中心的位置") }
    }
}
