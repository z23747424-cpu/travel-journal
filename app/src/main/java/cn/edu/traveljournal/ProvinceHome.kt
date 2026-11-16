package cn.edu.traveljournal

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ProvinceSelector(selected:String?,onSelect:(String?)->Unit,allowNone:Boolean=true){
    val context=LocalContext.current;val atlas=remember{CityAtlas.load(context)};var expanded by remember{mutableStateOf(false)}
    Box{OutlinedButton(onClick={expanded=true}){Text(atlas.provinceAreas.find{it.code==selected}?.name?:if(allowNone)"不关联国内省份" else "选择省份")}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=380.dp)){
            if(allowNone)DropdownMenuItem(text={Text("不关联国内省份")},onClick={onSelect(null);expanded=false})
            atlas.provinceAreas.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={onSelect(p.code);expanded=false})}
        }
    }
}

@Composable private fun MapCrosshair(modifier:Modifier=Modifier){
    val ink=MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(48.dp)){
        val c=Offset(size.width/2f,size.height/2f)
        val gap=10.dp.toPx();val len=20.dp.toPx();val thin=1.4.dp.toPx()
        // 先铺一层白底再压墨色细线，深省浅省上都看得清。
        listOf(Color.White.copy(alpha=.6f) to thin*2.4f,ink.copy(alpha=.55f) to thin).forEach{(color,stroke)->
            drawLine(color,Offset(c.x,c.y-gap),Offset(c.x,c.y-len),stroke,StrokeCap.Round)
            drawLine(color,Offset(c.x,c.y+gap),Offset(c.x,c.y+len),stroke,StrokeCap.Round)
            drawLine(color,Offset(c.x-gap,c.y),Offset(c.x-len,c.y),stroke,StrokeCap.Round)
            drawLine(color,Offset(c.x+gap,c.y),Offset(c.x+len,c.y),stroke,StrokeCap.Round)
        }
    }
}

@Composable fun ProvinceHome(journals:List<Journal>,repo:JournalRepository,onNew:(Journal)->Unit,onOpen:(Journal)->Unit,onArchive:()->Unit,onDemo:()->Unit,hasDraft:Boolean,onResume:()->Unit,onError:(String)->Unit,designs:List<ProvinceDesign>,plans:List<TravelPlan>,onSaveDesign:suspend (ProvinceDesign)->Unit,enterAtMaxZoom:Boolean=true){
    val context=LocalContext.current;val atlas=remember{CityAtlas.load(context)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)};var editing by rememberSaveable{mutableStateOf<String?>(null)};var filter by rememberSaveable{mutableStateOf("all")};var search by remember{mutableStateOf(false)};var pickingNew by remember{mutableStateOf(false)}
    val state=remember(journals,plans){provinceMapState(atlas,journals,plans)};val styles=remember(designs){designs.associateBy{it.provinceCode}}
    var map by remember{mutableStateOf<ProvinceMapView?>(null)}
    // 省图层：放大地图或点「看区县」时只画这个省下面的区县。
    var focus by rememberSaveable{mutableStateOf<String?>(null)}
    var countyUnits by remember{mutableStateOf<List<AtlasCounty>>(emptyList())}
    val countyState=remember(journals,plans,countyUnits){countyMapState(journals,plans,countyUnits)}
    LaunchedEffect(focus){val code=focus;countyUnits=emptyList();if(code!=null)countyUnits=withContext(Dispatchers.IO){CountyAtlas.units(context,code)}}
    var images by remember{mutableStateOf<Map<String,List<android.graphics.Bitmap>>>(emptyMap())};var markers by remember{mutableStateOf<Map<String,android.graphics.Bitmap>>(emptyMap())}
    val requests=remember(designs,state){provinceImageRequests(designs,state.visited,state.marked)}
    LaunchedEffect(requests){
        var pending:ProvinceBitmaps?=null
        try{val loaded=withContext(Dispatchers.IO){loadProvinceBitmaps(requests,repo::photoFile).also{pending=it}}
            images=loaded.photos;markers=loaded.icons;pending=null
        }finally{pending?.let{set->(set.photos.values.flatten()+set.icons.values).distinct().forEach{it.recycle()}}}
    }
    BackHandler(selected!=null||focus!=null){if(selected!=null){selected=null;filter="all"}else focus=null}
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        Image(painterResource(R.drawable.scrapbook_paper),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alpha=.38f)
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={if(selected!=null){selected=null;filter="all"}else onArchive()}){if(selected!=null)TravelIcon(R.drawable.ic_lvye_arrow_left,null);Text(if(selected!=null)"全国" else "旅页 · 手账")}
                Spacer(Modifier.weight(1f));TextButton(onClick=onArchive){Text("全部手账")}
            }
            val province=atlas.provinceAreas.find{it.code==selected}
            if(province==null){
                Row(verticalAlignment=Alignment.CenterVertically){Text("我的旅行地图",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f).padding(top=22.dp));ProvinceDecoration(modifier=Modifier.size(75.dp,40.dp))}
                Text("把走过的地方，留成回忆",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${state.visited.size} 个省份 · ${journals.count{it.visited}} 页回忆",style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f));TextButton(onClick={pickingNew=false;search=true}){Text("找省份")}}
                val focused=atlas.provinceAreas.find{it.code==focus}
                if(focused!=null)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${focused.shortName} · ${countyUnits.size} 个区县",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f));TextButton(onClick={map?.showChina()}){Text("返回全国")}}
                Box(Modifier.weight(1f).fillMaxWidth()){
                    AndroidView(factory={ctx->ProvinceMapView(ctx).also{map=it}},update={v->v.atlas=atlas;v.visited=state.visited;v.marked=state.marked;v.designs=styles;v.photos=images;v.markerPhotos=markers
                        v.focusProvince=focus;v.counties=countyUnits;v.countyVisited=countyState.visited;v.countyMarked=countyState.marked
                        v.enterProvinceAtMaxZoom=enterAtMaxZoom
                        v.onProvince={selected=it}
                        v.onMarker={code->if(focus==null)editing=code else atlas.provinceAreas.find{it.code==focus}?.let{editing=it.code}}
                        v.onDrillProvince={if(focus==null)focus=it}
                        v.onLeaveProvince={if(focus!=null){focus=null;countyUnits=emptyList()}}
                        v.onLongProvince={code->onNew(Journal(provinceCode=code,place=atlas.provinceAreas.find{p->p.code==code}?.name.orEmpty()))}
                        v.onCounty={code->countyUnits.find{it.code==code}?.let{c->onNew(Journal(provinceCode=focus,city=c.name,place=c.name,latitude=c.center.latitude,longitude=c.center.longitude))}}
                        v.onLongCounty={code->countyUnits.find{it.code==code}?.let{c->onNew(Journal(provinceCode=focus,city=c.name,place=c.name,latitude=c.center.latitude,longitude=c.center.longitude))}}
                    },modifier=Modifier.fillMaxSize())
                    // 固定的十字架：十字架指着的省就是放大后要进的省，进省后收起来。
                    if(focus==null)MapCrosshair(Modifier.align(Alignment.Center))
                    Column(Modifier.align(Alignment.BottomEnd),verticalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick={map?.zoomBy(1.4f)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)){TravelIcon(R.drawable.ic_lvye_plus,"放大地图")};FilledTonalButton(onClick={map?.zoomBy(1/1.4f)},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)){TravelIcon(R.drawable.ic_lvye_minus,"缩小地图")};TextButton(onClick={if(focus!=null)map?.showChina() else map?.reset()},modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Text(if(focus!=null)"全国" else "归位")}}
                }
                Text(if(focus!=null)"点一个区县，记下在那里的一程 · 双指放大也能进来" else "点一个省，翻开那里的回忆 · 放大可见区县 · 长按记一笔",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(hasDraft)TextButton(onClick=onResume){Text("继续未完成的手账 ›")}
                val recent=journals.filter{it.visited}.maxByOrNull{it.updatedAt}
                if(recent!=null)Row(Modifier.fillMaxWidth().clickable{onOpen(recent)}.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){journalCover(recent)?.let{ProvincePhotoFrame(repo,it,"最近的旅行照片",Modifier.size(94.dp,58.dp))};Column(Modifier.weight(1f)){Text(recent.provinceCode?.let{code->atlas.provinceAreas.find{it.code==code}?.shortName}.orEmpty().ifBlank{"最近一页"},style=MaterialTheme.typography.labelMedium);Text(journalDisplayTitle(recent),maxLines=1,style=MaterialTheme.typography.titleMedium)}}
                else TextButton(onClick=onDemo){Text("第一程，会落在哪里？ · 看看示例")}
                Button(onClick={pickingNew=true;search=true},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)){TravelIcon(R.drawable.ic_lvye_plus,null);Spacer(Modifier.width(6.dp));Text("记一笔")}
            }else{
                val records=journals.filter{journalProvinceCode(it,atlas)==province.code}.sortedByDescending{it.date}
                Row{Text(province.shortName,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f).padding(top=18.dp));ProvinceDecoration(true,Modifier.size(86.dp,65.dp))};Text("把这一程慢慢收藏",modifier=Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row{TextButton(onClick={editing=province.code}){Text("封面与省份装饰")};TextButton(onClick={onNew(Journal(provinceCode=province.code,place=province.name))}){TravelIcon(R.drawable.ic_lvye_plus,null);Spacer(Modifier.width(6.dp));Text("记一笔")}}
                val design=styles[province.code];val cover=design?.photos?.getOrNull(design.coverIndex)?:records.flatMap{it.photos}.firstOrNull()
                if(cover!=null)ProvincePhotoFrame(repo,cover,province.name+"封面",Modifier.fillMaxWidth().height(160.dp))
                Row{listOf("all" to "全部","visited" to "已去","wish" to "想去").forEach{(key,label)->FilterChip(selected=filter==key,onClick={filter=key},label={Text(label)},modifier=Modifier.padding(end=8.dp))}}
                val shown=records.filter{filter=="all"||it.visited==(filter=="visited")}
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(shown.isEmpty())item{Text("这里还有空白，等下一程留下故事。",Modifier.padding(vertical=30.dp))}
                    items(shown,key={it.id}){j->Column(Modifier.fillMaxWidth().clickable{onOpen(j)}.padding(vertical=6.dp)){
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            journalCover(j)?.let{ProvincePhotoFrame(repo,it,"手账照片",Modifier.size(92.dp))}
                            Column(Modifier.weight(1f)){Text(j.date+if(j.visited)" · 已去" else " · 想去",style=MaterialTheme.typography.labelMedium);Text(journalDisplayTitle(j),style=MaterialTheme.typography.titleMedium);Text(j.body.take(76),style=MaterialTheme.typography.bodyMedium,maxLines=2)}
                        }
                    }}
                }
            }
        }
    }
    if(search)AlertDialog(onDismissRequest={search=false},title={Text("这一程，去哪里？")},text={Column{ProvinceSelector(null,{code->if(code!=null){search=false;if(pickingNew)onNew(Journal(provinceCode=code,place=atlas.provinceAreas.find{it.code==code}?.name.orEmpty()))else selected=code}},false);Text(if(pickingNew)"选择所属省份，开始写这一页。" else "选省翻相册，或在地图长按省份记一笔。")}},confirmButton={TextButton(onClick={search=false}){Text("关闭")}})
    editing?.let{code->ProvinceDesignDialog(styles[code]?:ProvinceDesign(code),repo,{editing=null},{d->try{onSaveDesign(d);editing=null}catch(e:Exception){onError(e.message?:"保存失败");throw e}})}
}

@Composable private fun ProvinceDesignDialog(initial:ProvinceDesign,repo:JournalRepository,onDismiss:()->Unit,onSave:suspend (ProvinceDesign)->Unit){
    var draft by remember(initial.provinceCode){mutableStateOf(initial)};var message by remember{mutableStateOf("")};var saving by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    var provinceColor by remember(initial.provinceCode){mutableStateOf(initial.color)};var markerColor by remember(initial.provinceCode){mutableStateOf(initial.marker.color)}
    val imports=remember(initial.provinceCode){ProvinceImportSession(repo::discardUnusedPhotos)}
    DisposableEffect(imports){onDispose{imports.close()}}
    LaunchedEffect(draft.photos,draft.marker.image){imports.cleanup((draft.photos+listOfNotNull(draft.marker.image)).toSet())}
    var markerPick by remember{mutableStateOf(false)};var importing by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){val isMarker=markerPick;scope.launch{importing=true;try{val name=withContext(NonCancellable+Dispatchers.IO){repo.importPhoto(uri).also{imports.record(it)}};currentCoroutineContext().ensureActive();draft=if(isMarker)draft.copy(marker=draft.marker.copy(kind="image",image=name))else draft.copy(mode="photos",photos=(draft.photos+name).distinct().take(4))}catch(e:kotlinx.coroutines.CancellationException){imports.close();throw e}catch(e:Exception){message=e.message?:"图片导入失败"}finally{importing=false}}}}
    AlertDialog(onDismissRequest={if(!saving&&!importing)onDismiss()},title={Text("把这个省，装进手账")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("省份填色 · 去过后铺满整个省")
        Row{listOf("#B8CBB3","#DAC8A6","#C3CED0","#D8B5A5").forEach{color->TextButton(onClick={provinceColor=color;draft=draft.copy(color=color,mode="color")},modifier=Modifier.size(48.dp),contentPadding=PaddingValues(4.dp)){Box(Modifier.size(30.dp).background(Color(android.graphics.Color.parseColor(color))))}}}
        OutlinedTextField(value=provinceColor,onValueChange={provinceColor=it.take(7);draft=draft.copy(mode="color")},label={Text("自定义省份颜色 #RRGGBB")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("独立照片拼贴 ${draft.photos.size}/4 · 不随新手账改变");if(draft.photos.isNotEmpty())TextButton(onClick={draft=draft.copy(mode="photos")}){if(draft.mode=="photos")TravelIcon(R.drawable.ic_lvye_check,null);Text("使用照片铺省")}
        Row{TextButton(onClick={markerPick=false;picker.launch("image/*")},enabled=draft.photos.size<4){Text("添加照片")};TextButton(onClick={draft=draft.copy(photos=emptyList(),coverIndex=0,mode="color")}){Text("清空拼贴")}}
        draft.photos.forEachIndexed{i,name->Row(verticalAlignment=Alignment.CenterVertically){LocalPhoto(repo.photoFile(name),"省份照片",Modifier.size(48.dp));RadioButton(selected=draft.coverIndex==i,onClick={draft=draft.copy(coverIndex=i)});Text("用作相册封面",style=MaterialTheme.typography.labelMedium)}}
        HorizontalDivider();Text("想去 / 计划标记")
        Row{listOf("flag" to "旗","pin" to "钉","star" to "星","heart" to "心").forEach{(kind,label)->TextButton(onClick={draft=draft.copy(marker=draft.marker.copy(kind=kind))},modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Column(horizontalAlignment=Alignment.CenterHorizontally){when(kind){"flag"->TravelIcon(R.drawable.ic_lvye_flag,null);"pin"->TravelIcon(R.drawable.ic_lvye_map_pin,null);"star"->Icon(Icons.Outlined.StarBorder,null,Modifier.size(22.dp));else->Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(22.dp))};Row{if(draft.marker.kind==kind)TravelIcon(R.drawable.ic_lvye_check,"已选择");Text(label)}}}}}
        TextButton(onClick={markerPick=true;picker.launch("image/*")}){Text("选本地图片作标记（保留透明）")}
        Row{listOf("#AC7850","#4C6B52","#A35F5B","#647E9A").forEach{color->TextButton(onClick={markerColor=color;draft=draft.copy(marker=draft.marker.copy(color=color))},modifier=Modifier.size(48.dp),contentPadding=PaddingValues(4.dp)){Box(Modifier.size(28.dp).background(Color(android.graphics.Color.parseColor(color))))}}}
        OutlinedTextField(value=markerColor,onValueChange={markerColor=it.take(7)},label={Text("自定义标记颜色 #RRGGBB")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("标记大小 ${draft.marker.size} dp");Slider(value=draft.marker.size.toFloat(),onValueChange={draft=draft.copy(marker=draft.marker.copy(size=it.toInt()))},valueRange=16f..40f,steps=23)
        if(importing)Text("正在保存本地原图…");if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=!saving&&!importing,onClick={scope.launch{saving=true;try{val toSave=draft.copy(color=provinceColor,marker=draft.marker.copy(color=markerColor));validateProvinceDesign(toSave);onSave(toSave);imports.cleanup()}catch(e:Exception){message=e.message?:"保存失败，请重试"}finally{saving=false}}}){Text(if(saving)"保存中…" else "保存设计")}},dismissButton={TextButton(enabled=!saving&&!importing,onClick=onDismiss){Text("取消")}})
}

@Composable internal fun ProvinceDecoration(willow:Boolean=false,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val bitmap=remember(willow){val sheet=BitmapFactory.decodeResource(context.resources,R.drawable.scrapbook_decor);if(willow)android.graphics.Bitmap.createBitmap(sheet,35,15,555,600)else android.graphics.Bitmap.createBitmap(sheet,625,230,620,220)}
    Image(bitmap=bitmap.asImageBitmap(),contentDescription=null,modifier=modifier,contentScale=ContentScale.Fit,alpha=.7f)
}

@Composable internal fun ProvincePhotoFrame(repo:JournalRepository,name:String,label:String,modifier:Modifier=Modifier){
    Box(modifier.background(Color.White).padding(7.dp)){LocalPhoto(repo.photoFile(name),label,Modifier.fillMaxSize())}
}














