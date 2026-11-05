package cn.edu.traveljournal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun SearchScreen(journals: List<Journal>, repo: JournalRepository, onOpen: (Journal)->Unit, onSettings:()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("")}
    var hits by remember {mutableStateOf<List<SearchHit>>(emptyList())}
    var searched by remember {mutableStateOf(false)}
    var method by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    val scopeJournals=journals.filter { filter.isBlank() || it.date.contains(filter.trim()) || it.place.contains(filter.trim(),true) }
    DisposableEffect(Unit) {onDispose{job?.cancel()}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageTitle("找回一个瞬间", "不必记住标题，描述你还记得的画面。")}
        item {OutlinedTextField(query,{query=it},label={Text("想找哪一段回忆？")},placeholder={Text("比如：那次海边的日落")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)}
        item {OutlinedTextField(filter,{filter=it},label={Text("限定年份或地点（可选）")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(enabled=query.isNotBlank()&&job?.isActive!=true,onClick={
                job=scope.launch {
                    error="";searched=true;method="精灵智能搜索";hits=emptyList()
                    try {
                        if(scopeJournals.isEmpty()){hits=emptyList();return@launch}
                        val c=withContext(Dispatchers.IO){repo.loadConfig()}
                        hits=AiClient().search(c,query,scopeJournals)
                    }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"智能搜索暂不可用";hits=localSearch(query,scopeJournals);method="本地关键词结果（智能搜索未完成）"}finally{job=null}
                }
            }){Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("精灵搜索")}
            OutlinedButton(enabled=query.isNotBlank()&&job?.isActive!=true,onClick={hits=localSearch(query,scopeJournals);searched=true;method="本地关键词搜索";error=""}){Text("本地搜索")}
        }}
        if(job?.isActive==true) item{AiProgress("正在寻找有关的回忆…"){searched=false;job?.cancel()}}
        if(error.isNotBlank()) item {Column {Message(error,true);TextButton(onClick=onSettings){Text("检查 AI 设置")}}}
        if(!searched) item {Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
            EmptyLandscape()
            Text("记得风景，就能再遇见",style=MaterialTheme.typography.titleLarge)
            Text("本地搜索查找文字与标签；精灵搜索理解相近的表达。智能搜索会将范围内的手账文字发送给你配置的 AI。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        if(searched&&job?.isActive!=true) item {Text("$method · ${hits.count{hit->scopeJournals.any{it.id==hit.id}}} 条结果",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
        if(searched&&hits.isEmpty()&&job?.isActive!=true) item {Message(if(journals.isEmpty())"还没有手账。先记录一段旅行，再来寻找回忆。" else "没有找到相符的记录，试试换个描述或放宽筛选范围。")}
        items(hits.distinctBy{it.id},key={it.id}) {hit-> scopeJournals.find{it.id==hit.id}?.let{j->JournalCard(j,repo,hit.reason){onOpen(j)}} }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun InspirationScreen(wishes:List<Wish>,repo:JournalRepository,
    onSaveWish:(Wish)->Unit,onDeleteWish:(String)->Unit,onStart:(Wish)->Unit,onSettings:()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var origin by rememberSaveable {mutableStateOf("")}
    var duration by rememberSaveable {mutableStateOf("")}
    var budget by rememberSaveable {mutableStateOf("")}
    var details by rememberSaveable {mutableStateOf(false)}
    var wishlist by rememberSaveable {mutableStateOf(false)}
    var result by remember {mutableStateOf<RecommendationResult?>(null)}
    var destinations by remember {mutableStateOf<List<Destination>>(emptyList())}
    var excluded by remember {mutableStateOf<Set<String>>(emptySet())}
    var lastQuery by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)}
    val scope=rememberCoroutineScope()
    DisposableEffect(Unit){onDispose{job?.cancel()}}
    fun recommend(again:Boolean=false) {
        val request=listOf(query,origin.takeIf{it.isNotBlank()}?.let{"出发地：$it"},duration.takeIf{it.isNotBlank()}?.let{"天数：$it"},budget.takeIf{it.isNotBlank()}?.let{"预算：每人${it}元人民币，含主要交通住宿餐饮"}).filterNotNull().joinToString("；")
        if(request.isBlank()) return
        val omit=if(again&&request==lastQuery) excluded+destinations.map{it.name} else emptySet()
        excluded=omit;lastQuery=request;error="";result=null
        job=scope.launch {
            try {val online=OnlineTravelClient().recommend(withContext(Dispatchers.IO){repo.loadConfig()},request,omit);destinations=online.destinations;result=online.result}
            catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"推荐暂不可用，请稍后再试"}finally{job=null}
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { PageTitle(if(wishlist)"我的想去清单" else "下一站，心之所向",if(wishlist)"先把期待收藏，等一个出发的日子。" else "从一个念头，找到下一段旅程。") }
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!wishlist,onClick={wishlist=false},label={Text("寻找灵感")})
            FilterChip(wishlist,onClick={wishlist=true},label={Text("想去清单 · ${wishes.size}")})
        }}
        if(wishlist) {
            if(wishes.isEmpty()) item{EmptyLandscape();Message("还没有收藏的目的地。找到心动的一站，就把它留在这里。")}
            items(wishes,key={it.id}) {wish -> WishCard(wish,onSaveWish,onDeleteWish,onStart)}
        } else {
            item {OutlinedTextField(query,{query=it},label={Text("这次想怎样旅行？")},placeholder={Text("想看海，人少一点，适合周末放空…")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)}
            item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("看海与日落","山野间徒步","古城慢慢逛","街巷里觅食").forEach{tag->SuggestionChip(onClick={query=tag},label={Text(tag)})}
            }}
            item {TextButton(onClick={details=!details}){Text(if(details)"收起出行条件" else "补充出发地、天数与预算");Icon(if(details)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)}}
            if(details) {
                item{OutlinedTextField(origin,{origin=it},label={Text("出发城市")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedTextField(duration,{duration=it},label={Text("出行天数")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(budget,{budget=it},label={Text("每人预算 / 元")},modifier=Modifier.weight(1f),singleLine=true)
                }}
            }
            item {Button(onClick={recommend()},enabled=query.isNotBlank()&&job?.isActive!=true,modifier=Modifier.fillMaxWidth()){
                Icon(Icons.Outlined.TravelExplore,null);Spacer(Modifier.width(8.dp));Text("联网搜索，寻找下一站")
            }}
            if(job?.isActive==true) item{AiProgress("正在联网搜索并整理旅行建议…"){job?.cancel()}}
            if(error.isNotBlank()) item {Column{Message(error,true);TextButton(onClick=onSettings){Text("检查 AI 设置")}}}
            result?.let{r->
                if(r.understood.isNotBlank()) item{Text("精灵理解的需求",style=MaterialTheme.typography.titleMedium);Message(r.understood)}
                if(r.items.isEmpty()) item{Message("这次联网资料没有提供可靠的推荐。可以补充偏好或换个描述再试。")}
                items(r.items,key={it.id}) {recommendation-> destinations.find{it.id==recommendation.id}?.let{d->
                    val saved=wishes.any{it.destination.id==d.id}
                    DestinationCard(d,recommendation,saved){onSaveWish(Wish(destination=d,query=lastQuery,reason=recommendation.reason+recommendation.caution.takeIf{it.isNotBlank()}?.let{"\n待确认：$it"}.orEmpty()))}
                }}
                if(r.items.isNotEmpty()) item{OutlinedButton(onClick={recommend(true)},enabled=job?.isActive!=true,modifier=Modifier.fillMaxWidth()){Text("不合心意，换一批")}}
            }
            if(result==null) item{EmptyLandscape();Spacer(Modifier.height(14.dp));Text("想出发的理由，可以很简单。",style=MaterialTheme.typography.titleLarge)}
            item{Text("基于本次联网搜索，不限定预置目的地。推荐附网页来源；预算、交通、天气和开放情况请出发前核实。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}

@Composable private fun DestinationCard(d:Destination,r:Recommendation,saved:Boolean,onSave:()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(d.region,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        Text(d.name,style=MaterialTheme.typography.headlineMedium)
        TagRow(d.tags)
        Text(r.reason.ifBlank{d.summary},style=MaterialTheme.typography.bodyLarge)
        if(d.days.isNotBlank()||d.season.isNotBlank())Text(listOf(d.days,d.season).filter{it.isNotBlank()}.joinToString("\n"),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(r.caution.isNotBlank()) Text("待确认：${r.caution}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.secondary)
        SourceLink(d)
        OutlinedButton(onClick=onSave,enabled=!saved){Icon(if(saved)Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd,null);Spacer(Modifier.width(8.dp));Text(if(saved)"已在想去清单" else "收藏这份期待")}
    }
}

@Composable private fun SourceLink(d:Destination) {
    val context=LocalContext.current
    TextButton(onClick={
        val uri=Uri.parse(d.sourceUrl)
        if(uri.scheme in listOf("https","http")) runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,uri))}
    }){Icon(Icons.Outlined.OpenInNew,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("查看目的地资料来源")}
}

@Composable private fun WishCard(wish:Wish,onSave:(Wish)->Unit,onDelete:(String)->Unit,onStart:(Wish)->Unit) {
    var expanded by rememberSaveable(wish.id){mutableStateOf(false)}
    var notes by rememberSaveable(wish.id,wish.notes){mutableStateOf(wish.notes)}
    var deleting by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable{expanded=!expanded}){
                Text(wish.destination.region,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(wish.destination.name,style=MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick={expanded=!expanded}){Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"展开收藏")}
        }
        Text(if(wish.visited)"已经去过，期待下一次重逢" else "还在期待中的旅程",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        Text(wish.reason.ifBlank{wish.destination.summary},style=MaterialTheme.typography.bodyMedium)
        if(expanded){
            Text("当时的想法：${wish.query}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(notes,{notes=it},label={Text("给这次旅行留点备注")},modifier=Modifier.fillMaxWidth())
            TextButton(onClick={onSave(wish.copy(notes=notes))}){Text("保存备注")}
            SourceLink(wish.destination)
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(wish.visited,{onSave(wish.copy(visited=it))});Text("我已经去过这里")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            OutlinedButton(onClick={onStart(wish)}){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(6.dp));Text("开始记录这次旅行")}
            IconButton(onClick={deleting=true}){Icon(Icons.Outlined.DeleteOutline,"移除收藏")}
        }
    }
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("移除这份期待？")},text={Text("关联的已保存手账会保留。")},confirmButton={TextButton(onClick={deleting=false;onDelete(wish.id)}){Text("移除")}},dismissButton={TextButton(onClick={deleting=false}){Text("保留")}})
}
