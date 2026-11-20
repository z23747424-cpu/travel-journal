package cn.edu.traveljournal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import kotlinx.coroutines.*

/**
 * 这个文件只保留仍在使用的「找回一个瞬间」（搜索）页面。
 *
 * 说明：这里曾经还有一个 Compose 版 `InspirationScreen`，v1.6 起灵感页已经换成
 * `NativeInspirationScreen`（MainActivity 里 tab==2 调用的就是它），旧版不再有任何调用方。
 * 之前在旧版里加「AI 网页版推荐」按钮，装到手机上却看不到任何变化，就是因为改的是这份死代码。
 * 旧实现已在 v1.6.1 删除；灵感页的新功能请只写在 NativeInspirationScreen.kt 里。
 */

@Composable fun SearchScreen(journals: List<Journal>, repo: JournalRepository, onOpen: (Journal)->Unit, onSettings:()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("")}
    var hits by remember {mutableStateOf<List<SearchHit>>(emptyList())}
    var searched by remember {mutableStateOf(false)}
    var method by remember {mutableStateOf("")}
    var error by remember {mutableStateOf("")}
    var job by remember {mutableStateOf<Job?>(null)}
    var background by remember {mutableStateOf<File?>(null)}
    var backgroundBusy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit) { background = withContext(Dispatchers.IO){ repo.searchBackground() } }
    // 背景只在搜索页本地使用，复制进私有目录后不依赖相册授权。
    val backgroundPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            backgroundBusy=true
            try { background=withContext(Dispatchers.IO){repo.setSearchBackground(uri)} }
            catch(e:CancellationException){throw e}
            catch(e:Exception){error=e.message?:"背景图片读取失败，请换一张"}
            finally{backgroundBusy=false}
        }
    }
    val scopeJournals=journals.filter { filter.isBlank() || it.date.contains(filter.trim()) || it.place.contains(filter.trim(),true)||it.city.contains(filter.trim(),true) }
    DisposableEffect(Unit) {onDispose{job?.cancel()}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageTitle("找回一个瞬间", "不必记住标题，描述你还记得的画面。")}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=!backgroundBusy,onClick={backgroundPicker.launch("image/*")}){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text(if(backgroundBusy)"保存中" else if(background==null)"换一张背景图" else "换一张背景图")}
            if(background!=null) TextButton(enabled=!backgroundBusy,onClick={scope.launch{runCatching{withContext(Dispatchers.IO){repo.clearSearchBackground()}}.onSuccess{background=null}.onFailure{error=it.message?:"恢复默认背景失败"}}}){Text("恢复默认背景")}
        } }
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
            }){TravelIcon(R.drawable.ic_lvye_sparkles,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("精灵搜索")}
            OutlinedButton(enabled=query.isNotBlank()&&job?.isActive!=true,onClick={hits=localSearch(query,scopeJournals);searched=true;method="本地关键词搜索";error=""}){Text("本地搜索")}
        }}
        if(job?.isActive==true) item{AiProgress("正在寻找有关的回忆…"){searched=false;job?.cancel()}}
        if(error.isNotBlank()) item {Column {Message(error,true);TextButton(onClick=onSettings){Text("检查 AI 设置")}}}
        if(!searched) item {Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
            EmptyLandscape(photo=background)
            Text("记得风景，就能再遇见",style=MaterialTheme.typography.titleLarge)
            Text("本地搜索查找文字与标签；精灵搜索理解相近的表达。智能搜索会将范围内的手账文字发送给你配置的 AI。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        if(searched&&job?.isActive!=true) item {Text("$method · ${hits.count{hit->scopeJournals.any{it.id==hit.id}}} 条结果",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
        if(searched&&hits.isEmpty()&&job?.isActive!=true) item {Message(if(journals.isEmpty())"还没有手账。先记录一段旅行，再来寻找回忆。" else "没有找到相符的记录，试试换个描述或放宽筛选范围。")}
        items(hits.distinctBy{it.id},key={it.id}) {hit-> scopeJournals.find{it.id==hit.id}?.let{j->JournalCard(j,repo,hit.reason){onOpen(j)}} }
    }
}