package cn.edu.traveljournal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val MAP_HOST = "appassets.androidplatform.net"
private const val MAP_URL = "https://$MAP_HOST/assets/map/index.html"

/** Bridge exists only on bundled, HTTPS-origin content; all other main-frame navigation is blocked. */
internal class MapBridge(
    private val dispatch: (() -> Unit) -> Unit,
    private val onReady: () -> Unit,
    private val onPoint: (MapPoint) -> Unit,
    private val onNotes: (String) -> Unit,
    private val onCamera: (MapPoint, Double) -> Unit,
    private val onMove: (String,MapPoint) -> Unit = {_,_->},
) {
    @JavascriptInterface fun ready() = dispatch(onReady)
    @JavascriptInterface fun selectPoint(latitude: Double, longitude: Double) {
        if (validCoordinates(latitude, longitude)) dispatch { onPoint(MapPoint(latitude, longitude)) }
    }
    @JavascriptInterface fun openNotes(ids: String) { if (ids.length <= 256_000) dispatch { onNotes(ids) } }
    @JavascriptInterface fun cameraChanged(latitude: Double, longitude: Double, zoom: Double) {
        if (validCoordinates(latitude, longitude) && zoom.isFinite() && zoom in 2.0..19.0) dispatch { onCamera(MapPoint(latitude, longitude), zoom) }
    }
    @JavascriptInterface fun moveNote(id:String,latitude:Double,longitude:Double) {
        if(id.length in 1..128&&validCoordinates(latitude,longitude))dispatch{onMove(id,MapPoint(latitude,longitude))}
    }
}

@SuppressLint("SetJavaScriptEnabled") // Only allowlisted bundled resources can execute code.
@Composable internal fun MapSurface(
    journals: List<Journal>, modifier: Modifier = Modifier, initialPoint: MapPoint? = null, initialZoom: Double = 13.0,
    onPoint: (MapPoint) -> Unit, onNotes: (String) -> Unit = {}, onCamera: (MapPoint, Double) -> Unit = { _, _ -> },
    chooseCenter: Boolean = false,
    onMove:(String,MapPoint)->Unit={_,_->},redrawKey:Int=0,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pointCallback by rememberUpdatedState(onPoint)
    val notesCallback by rememberUpdatedState(onNotes)
    val cameraCallback by rememberUpdatedState(onCamera)
    val moveCallback by rememberUpdatedState(onMove)
    val data by rememberUpdatedState(mapNotesJson(journals))
    var ready by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(231,236,229))
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString += " TravelJournal/1.2.0 (+https://github.com/z23747424-cpu/travel-journal)"
            addJavascriptInterface(MapBridge(
                dispatch = { action -> post { action() } },
                onReady = { ready = true; pageError = false },
                onPoint = { pointCallback(it) }, onNotes = { notesCallback(it) }, onCamera = { p,z -> cameraCallback(p,z) },
                onMove={id,p->moveCallback(id,p)},
            ), "AndroidMap")
            webViewClient = object : WebViewClient() {
                private val resources=LocalMapResources(context)
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    return resources.intercept(request.url,request.method,request.isForMainFrame)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.toString() == MAP_URL) return false
                    if (request.hasGesture() && request.url.scheme == "https" && request.url.host in setOf("www.openstreetmap.org", "leafletjs.com","datav.aliyun.com")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    }
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { ready = false }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) pageError = true
                }
            }
            loadUrl(MAP_URL)
        }
    }
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> webView.onResume()
            Lifecycle.Event.ON_PAUSE -> webView.onPause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) webView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            webView.stopLoading(); webView.removeJavascriptInterface("AndroidMap"); webView.onPause(); webView.destroy()
        }
    }
    LaunchedEffect(ready) {
        if (ready) {
            val config = JSONObject().put("latitude", initialPoint?.latitude ?: JSONObject.NULL)
                .put("longitude", initialPoint?.longitude ?: JSONObject.NULL).put("zoom", initialZoom)
            webView.evaluateJavascript("window.TravelMap.configure($config);window.TravelMap.setNotes($data);", null)
        }
    }
    LaunchedEffect(data, ready,redrawKey) { if (ready) webView.evaluateJavascript("window.TravelMap.setNotes($data);", null) }
    LaunchedEffect(ready,reload) { if(!ready){delay(12_000);pageError=true} }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (!ready) Surface(Modifier.align(Alignment.Center), shape=MaterialTheme.shapes.medium) {
                Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    if (!pageError) { Text("正在打开旅行地图…") }
                    else { Text("地图暂时无法打开"); TextButton(onClick={pageError=false;reload++;webView.loadUrl(MAP_URL)}){Text("重新打开地图")} }
                }
            }
        }
        if (chooseCenter) TextButton(onClick={webView.evaluateJavascript("window.TravelMap.chooseCenter();",null)},enabled=ready,modifier=Modifier.fillMaxWidth()) { Text("选择地图中心的位置") }
    }
}
