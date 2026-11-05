package cn.edu.traveljournal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapHome(journals:List<Journal>,repo:JournalRepository,onNew:(Journal)->Unit,onOpen:(Journal)->Unit,onAll:()->Unit,
    onDemo:()->Unit,hasDraft:Boolean,onResume:()->Unit,onError:(String)->Unit,onMoveRecord:(String,MapPoint)->Unit,mapRevision:Int) {
    var cameraLat by rememberSaveable(key="china-map-lat-v12") { mutableStateOf<Double?>(null) }
    var cameraLon by rememberSaveable(key="china-map-lon-v12") { mutableStateOf<Double?>(null) }
    var cameraZoom by rememberSaveable(key="china-map-zoom-v12") { mutableDoubleStateOf(4.0) }
    var selectedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var selectedLon by rememberSaveable { mutableStateOf<Double?>(null) }
    var place by rememberSaveable { mutableStateOf("") }
    var visited by rememberSaveable { mutableStateOf(true) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var importing by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val located=journals.count { it.mapPoint()!=null }
    val selectedNotes=journals.filter { it.id in selectedIds }
    val point=if(selectedLat!=null&&selectedLon!=null)MapPoint(selectedLat!!,selectedLon!!)else null
    fun dismiss(){ selectedLat=null;selectedLon=null;selectedIds=emptyList();place="";visited=true }
    val latestPoint by rememberUpdatedState(point)
    val latestName by rememberUpdatedState(place)
    val latestVisited by rememberUpdatedState(visited)
    val illustrationPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val selected=latestPoint
        if(uri!=null&&selected!=null)scope.launch {
            importing=true
            try {
                val photo=withContext(Dispatchers.IO){repo.importPhoto(uri)}
                val name=latestName.trim().ifBlank{"我的旅行足迹"}
                val entry=Journal(title=name.take(200),place=name,latitude=selected.latitude,longitude=selected.longitude,
                    photos=listOf(photo),mapSticker=photo,tags=listOf("插画足迹"),visited=latestVisited)
                dismiss();onNew(entry)
            }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){onError(e.message?:"插画导入失败，请重新选择图片")}
            finally{importing=false}
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=16.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的旅行地图",style=MaterialTheme.typography.headlineMedium)
                Text("缩放中国全图，贴上你的旅行插画",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=onAll){Icon(Icons.Outlined.AutoStories,"全部手账")}
        }
        if(hasDraft) TextButton(onClick=onResume,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(8.dp));Text("继续未完成的手账")}
        MapSurface(journals,Modifier.weight(1f).fillMaxWidth(),
            initialPoint=if(cameraLat!=null&&cameraLon!=null)MapPoint(cameraLat!!,cameraLon!!)else null,initialZoom=cameraZoom,
            onPoint={p->selectedLat=p.latitude;selectedLon=p.longitude;selectedIds=emptyList();place="";visited=true},
            onNotes={json->runCatching{mapNoteIds(json,journals)}.onSuccess{ids->
                if(ids.size==1)journals.find{it.id==ids.single()}?.let(onOpen)else {dismiss();selectedIds=ids}
            }.onFailure{onError("请放大地图后再查看这些笔记")}},
            onCamera={p,z->cameraLat=p.latitude;cameraLon=p.longitude;cameraZoom=z},chooseCenter=true,redrawKey=mapRevision,onMove=onMoveRecord)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(journals.isEmpty())"把第一段旅程留在地图上" else "$located 篇已标位置 · ${journals.size-located} 篇待标注",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=if(journals.isEmpty())onDemo else onAll){Text(if(journals.isEmpty())"看看示例足迹" else "全部手账")}
        }
    }
    if(point!=null||selectedNotes.isNotEmpty()) ModalBottomSheet(onDismissRequest={if(!importing)dismiss()}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(point!=null) {
                Text("在这里，留下一页",style=MaterialTheme.typography.headlineSmall)
                Text(point.label(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(place,{place=it.take(300)},label={Text("给这个地点取个名字")},placeholder={Text("例如：杭州西湖、家乡的小巷")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(selected=visited,onClick={visited=true},label={Text("去过")});FilterChip(selected=!visited,onClick={visited=false},label={Text("未去过")})}
                Button(enabled=!importing,onClick={val entry=Journal(place=place.trim().ifBlank{"地图选点"},latitude=point.latitude,longitude=point.longitude,visited=visited);dismiss();onNew(entry)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(8.dp));Text("从这里开始记录")}
                OutlinedButton(enabled=!importing,onClick={if(hasDraft){dismiss();onResume()}else illustrationPicker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(importing)"正在保存插画…" else "用自己的插画标记这里")}
                Text("可从相册选择透明插画或照片；保存后可以拖动位置、调整大小。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("这里的 ${selectedNotes.size} 篇笔记",style=MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(selectedNotes,key={it.id}){entry->
                        TextButton(onClick={dismiss();onOpen(entry)},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                                Text(entry.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                                Text("${entry.date} · ${entry.place.ifBlank{"地图选点"}}",style=MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
                selectedNotes.firstOrNull()?.let{first->
                    if(selectedNotes.all{it.latitude==first.latitude&&it.longitude==first.longitude})OutlinedButton(onClick={dismiss();onNew(Journal(place=first.place,latitude=first.latitude,longitude=first.longitude,visited=first.visited))},modifier=Modifier.fillMaxWidth()){Text("在这个地点再写一篇")}
                }
            }
        }
    }
}

@Composable fun MapLocationPicker(initial:MapPoint?,initialName:String,onDismiss:()->Unit,onChoose:(MapPoint,String)->Unit) {
    var latitude by rememberSaveable { mutableStateOf(initial?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(initial?.longitude) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    BackHandler(onBack=onDismiss)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"取消地图选点")}
                    Text("为这一页标注位置",style=MaterialTheme.typography.titleLarge)
                }
                Text("缩放或移动地图，再点击具体地点。",Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.bodyMedium)
                MapSurface(emptyList(),Modifier.weight(1f).fillMaxWidth(),initialPoint=initial,onPoint={latitude=it.latitude;longitude=it.longitude},chooseCenter=true)
                Column(Modifier.padding(start=24.dp,end=24.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(latitude!=null&&longitude!=null)Text(MapPoint(latitude!!,longitude!!).label(),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name,{name=it.take(300)},label={Text("地点名称")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(enabled=latitude!=null&&longitude!=null,onClick={onChoose(MapPoint(latitude!!,longitude!!),name.trim())},modifier=Modifier.fillMaxWidth()){Text("使用这个位置")}
                }
            }
        }
    }
}
