package cn.edu.traveljournal

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapHome(journals:List<Journal>,repo:JournalRepository,onNew:(Journal)->Unit,onOpen:(Journal)->Unit,onAll:()->Unit,
    onDemo:()->Unit,hasDraft:Boolean,onResume:()->Unit,onError:(String)->Unit,onMoveRecord:(String,MapPoint)->Unit,mapRevision:Int,
    onSaveMapNote:(Journal)->Unit) {
    var cameraLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var cameraLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var cameraZoom by rememberSaveable { mutableDoubleStateOf(4.0) }
    var selectedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var selectedLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var place by rememberSaveable { mutableStateOf("") }
    var visited by rememberSaveable { mutableStateOf(true) }
    var fillColor by rememberSaveable { mutableStateOf("#A9BFAE") }
    var fillMode by rememberSaveable { mutableStateOf("color") }
    var selectedIds by remember { mutableStateOf(emptyList<String>()) }
    var photoUris by rememberSaveable(stateSaver=listSaver<List<Uri>,String>(save={it.map(Uri::toString)},restore={it.map(Uri::parse)})) { mutableStateOf(emptyList()) }
    var atlas by remember { mutableStateOf<CityAtlas?>(null) }
    var showCities by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    var focusedProvince by remember { mutableStateOf<AtlasProvince?>(null) }
    var importing by remember { mutableStateOf(false) }
    val controller=remember { NativeMapController() }
    val scope=rememberCoroutineScope()
    val uriHandler=LocalUriHandler.current
    val point=if(selectedLat!=null&&selectedLon!=null)MapPoint(selectedLat!!,selectedLon!!) else null
    val selectedCity=point?.let { atlas?.cityAt(it) }
    val selectedNotes=journals.filter { it.id in selectedIds }
    var journalCities by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    LaunchedEffect(journals,atlas) {
        val current=atlas
        journalCities=withContext(Dispatchers.IO) { journals.mapNotNull { j -> j.mapPoint()?.let { p -> current?.cityAt(p)?.code?.let { j.id to it } } }.toMap() }
    }
    val visitedCities=journals.asSequence().filter{it.visited}.mapNotNull{journalCities[it.id]}.toSet().size
    val flags=journals.count { !it.visited && it.mapPoint()!=null }
    fun dismiss(clearMarker:Boolean=true){if(clearMarker)controller.clearSelection();selectedLat=null;selectedLon=null;selectedIds=emptyList();place="";visited=true;fillColor="#A9BFAE";fillMode="color";photoUris=emptyList()}
    fun selectPoint(p:MapPoint){dismiss(clearMarker=false);selectedLat=p.latitude;selectedLon=p.longitude;place=atlas?.cityAt(p)?.name.orEmpty()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        photoUris=uris.take(12)
        if(uris.size>12)onError("每篇最多保存12张照片，已选择前12张")
        if(photoUris.isEmpty() && fillMode=="photos")fillMode="color"
    }
    Column(Modifier.fillMaxSize()) {
        if(focusedProvince!=null)TextButton(onClick={controller.showChina()},modifier=Modifier.padding(start=12.dp)){TravelIcon(R.drawable.ic_lvye_arrow_left,null);Spacer(Modifier.width(8.dp));Text("全国")}
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=12.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(focusedProvince?.let{"${it.shortName} · 旅行手账"} ?: "我的旅行地图",style=MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text("已去 $visitedCities 城 · 想去 $flags 处",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=onAll){TravelIcon(R.drawable.ic_lvye_book_open,"全部手账")}
        }
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            MapLegend("待出发",false);Spacer(Modifier.width(14.dp));MapLegend("已去过",true)
            Spacer(Modifier.width(14.dp));TravelIcon(R.drawable.ic_lvye_flag,null,Modifier.size(14.dp));Spacer(Modifier.width(4.dp));Text("想去",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick={showCredits=true}){TravelIcon(R.drawable.ic_lvye_info,"地图数据来源",Modifier.size(18.dp))}
        }
        if(hasDraft)TextButton(onClick=onResume,modifier=Modifier.fillMaxWidth()){Text("继续未完成的手账")}
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapSurface(journals,Modifier.fillMaxSize(),initialPoint=if(cameraLat!=null&&cameraLon!=null)MapPoint(cameraLat!!,cameraLon!!)else null,initialZoom=cameraZoom,
                onPoint={selectPoint(it)},onNotes={json->runCatching{mapNoteIds(json,journals)}.onSuccess{dismiss();selectedIds=it}.onFailure{onError("请放大地图后再查看这些笔记")}},
                onCamera={p,z->cameraLat=p.latitude;cameraLon=p.longitude;cameraZoom=z},onMove=onMoveRecord,redrawKey=mapRevision,repo=repo,controller=controller,onAtlasReady={atlas=it},onProvince={focusedProvince=it})
            MapZoomControls(controller,Modifier.align(Alignment.CenterEnd).padding(end=8.dp))
            OutlinedButton(onClick={showCities=true},modifier=Modifier.align(Alignment.BottomStart).padding(start=20.dp,bottom=16.dp)){TravelIcon(R.drawable.ic_lvye_search,null);Spacer(Modifier.width(6.dp));Text("找一座城")}
            FilledTonalButton(onClick={if(hasDraft)onResume() else onNew(Journal())},modifier=Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=16.dp)){TravelIcon(R.drawable.ic_lvye_plus,null);Spacer(Modifier.width(6.dp));Text("留下一程")}

        }
    }
    if(showCredits)AlertDialog(onDismissRequest={showCredits=false},title={Text("地图数据来源")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("行政边界使用 DataV.GeoAtlas，高德来源，课程学习交流用途。台湾县市数据来自 NLSC/MOI COUNTY_MOI_1140318（2025-03-18），由 pdmaptw v2.4.0 转换，采用 Open Government Data License 1.0。地图在本地绘制，无需联网地图服务。")
        TextButton(onClick={runCatching { uriHandler.openUri("https://help.aliyun.com/zh/datav/datav-7-0/user-guide/datav-geoatlas-widgets/") }.onFailure { onError("无法打开数据来源") }}){Text("DataV.GeoAtlas 资料")}
        TextButton(onClick={runCatching { uriHandler.openUri("https://data.gov.tw/en/datasets/7442") }.onFailure { onError("无法打开数据来源") }}){Text("NLSC 数据与开放授权")}
    }},confirmButton={TextButton(onClick={showCredits=false}){Text("知道了")}})
    if(showCities) {
        var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={showCities=false},title={Text("查找城市")},text={Column {
            OutlinedTextField(query,{query=it},label={Text("城市或省份")},singleLine=true,modifier=Modifier.fillMaxWidth())
            val cities=atlas?.cities.orEmpty().filter{query.isBlank()||it.name.contains(query,true)||it.provinceName.contains(query,true)}
            if(atlas==null)Text("正在加载城市边界…",Modifier.padding(12.dp))
            LazyColumn(Modifier.heightIn(max=420.dp)) { items(cities,key={it.code}) { city ->
                val count=journals.count{j->journalCities[j.id]==city.code}
                TextButton(onClick={showCities=false;controller.focus(city.center);val notes=journals.filter{j->journalCities[j.id]==city.code};if(notes.isEmpty())selectPoint(city.center) else {dismiss();selectedIds=notes.map{it.id}}},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=8.dp)) {
                    Text("${city.name} · ${city.provinceName}",Modifier.weight(1f));Text(if(count>0)"${count}篇" else "选择")
                }
            } }
        }},confirmButton={TextButton(onClick={showCities=false}){Text("关闭")}})
    }
    if(point!=null||selectedNotes.isNotEmpty()) ModalBottomSheet(onDismissRequest={if(!importing)dismiss()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(point!=null) {
                Text("在这里，留下一页",style=MaterialTheme.typography.headlineSmall)
                Text(listOfNotNull(selectedCity?.let{"${it.provinceName} · ${it.name}"},point.label()).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(place,{place=it.take(300)},label={Text("地点名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=visited,onClick={visited=true},label={Text("去过")});FilterChip(selected=!visited,onClick={visited=false},label={Text("想去 · 插旗")})}
                if(selectedCity==null)Text("暂无该地点的城市边界。仍可保存坐标、笔记或想去旗；城市图块填充仅在边界覆盖区域可用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(visited && selectedCity!=null)MapStyleControls(fillColor,fillMode,photoUris.isNotEmpty(),{fillColor=it},{fillMode=it})
                OutlinedButton(enabled=!importing,onClick={if(hasDraft)onResume() else picker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Text("选择照片 ${photoUris.size}/12")}
                Button(enabled=!importing,onClick={
                    if(hasDraft){onResume()}else scope.launch {
                        importing=true
                        try {
                            val imported=withContext(Dispatchers.IO){photoUris.mapNotNull{uri->runCatching{repo.importPhoto(uri)}.getOrNull()}}
                            val name=place.trim().ifBlank{selectedCity?.name ?: "地图选点"}
                            val entry=Journal(title=name.take(200),place=name,latitude=point.latitude,longitude=point.longitude,visited=visited,mapCityCode=selectedCity?.code,mapFillColor=fillColor,mapFillMode=if(imported.isNotEmpty())fillMode else "color",photos=imported)
                            withContext(Dispatchers.IO){repo.saveDraft(entry)}
                            if(imported.size<photoUris.size)onError("部分照片无法读取，已保留成功导入的照片")
                            dismiss();onNew(entry)
                        }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){onError(e.message?:"照片导入失败，请重试")}
                        finally{importing=false}
                    }
                },modifier=Modifier.fillMaxWidth()){Text(if(importing)"正在保存照片…" else "写手账")}
                if(!visited)OutlinedButton(enabled=!importing,onClick={val name=place.trim().ifBlank{selectedCity?.name ?: "地图选点"};val entry=Journal(title="想去$name".take(200),place=name,body="计划去$name。",latitude=point.latitude,longitude=point.longitude,visited=false,mapCityCode=selectedCity?.code);dismiss();onSaveMapNote(entry)},modifier=Modifier.fillMaxWidth()){Text("只插想去旗")}
            } else {
                Text("这里的 ${selectedNotes.size} 篇手账",style=MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(selectedNotes,key={it.id}) { entry ->
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(entry.photos.isNotEmpty())LocalPhoto(repo.photoFile(entry.photos.first()),entry.title,Modifier.fillMaxWidth().height(140.dp))
                            Text(entry.title,style=MaterialTheme.typography.titleMedium)
                            Text("${entry.date} · ${entry.place} · ${if(entry.visited)"去过" else "想去"}",style=MaterialTheme.typography.bodySmall)
                            if(entry.body.isNotBlank())Text(entry.body,maxLines=3,overflow=TextOverflow.Ellipsis)
                            OutlinedButton(onClick={dismiss();onOpen(entry)},modifier=Modifier.fillMaxWidth()){Text("打开这篇手账")}
                            HorizontalDivider()
                        }
                    }
                }
                selectedNotes.firstOrNull()?.let{first->first.mapPoint()?.let{p->OutlinedButton(onClick={selectPoint(p)},modifier=Modifier.fillMaxWidth()){Text("在这里再写一篇")}}}
            }
        }
    }
}

@Composable private fun MapLegend(label:String,filled:Boolean) {
    val color=MaterialTheme.colorScheme.primary
    Row(verticalAlignment=Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) { if(filled)drawRect(color.copy(alpha=.5f)) else drawRect(color.copy(alpha=.6f),style=Stroke(width=1.dp.toPx())) }
        Spacer(Modifier.width(5.dp));Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun MapLocationPicker(initial:MapPoint?,initialName:String,onDismiss:()->Unit,onChoose:(MapPoint,String)->Unit) {
    var latitude by rememberSaveable { mutableStateOf(initial?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(initial?.longitude) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val controller=remember { NativeMapController() }
    BackHandler(onBack=onDismiss)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"取消地图选点")};Text("为这一页标注位置",style=MaterialTheme.typography.titleLarge)}
                Text("长按地图选择地点，或移动地图后选择中心。",Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MapSurface(emptyList(),Modifier.fillMaxSize(),initialPoint=initial,onPoint={latitude=it.latitude;longitude=it.longitude},chooseCenter=true,controller=controller)
                    MapZoomControls(controller,Modifier.align(Alignment.CenterEnd).padding(end=8.dp))
                }
                Column(Modifier.padding(start=24.dp,end=24.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(latitude!=null&&longitude!=null)Text(MapPoint(latitude!!,longitude!!).label(),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name,{name=it.take(300)},label={Text("地点名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(enabled=latitude!=null&&longitude!=null,onClick={onChoose(MapPoint(latitude!!,longitude!!),name.trim())},modifier=Modifier.fillMaxWidth()){Text("使用这个位置")}
                }
            }
        }
    }
}

@Composable private fun MapZoomControls(controller:NativeMapController,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
            IconButton(onClick={controller.zoomIn()},modifier=Modifier.size(48.dp)){TravelIcon(R.drawable.ic_lvye_plus,"放大地图")}
        }
        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
            IconButton(onClick={controller.zoomOut()},modifier=Modifier.size(48.dp)){TravelIcon(R.drawable.ic_lvye_minus,"缩小地图")}
        }
        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
            IconButton(onClick={controller.showChina()},modifier=Modifier.size(48.dp)){TravelIcon(R.drawable.ic_lvye_map,"显示全国")}
        }
    }
}
