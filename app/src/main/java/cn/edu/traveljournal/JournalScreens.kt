package cn.edu.traveljournal

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable fun JournalHome(journals: List<Journal>, repo: JournalRepository, onNew: () -> Unit,
    onOpen: (Journal) -> Unit, onDemo: () -> Unit, onInspiration: () -> Unit,hasDraft:Boolean=false,onResume:()->Unit={}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start=24.dp,end=24.dp,bottom=100.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
        item { PageTitle("把旅途，留在这一页", if(journals.isEmpty()) "照片、文字，和那些舍不得忘记的瞬间。" else "${journals.size} 篇记录 · 按旅行日期排列") }
        if(hasDraft)item{OutlinedButton(onClick=onResume,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.EditNote,null);Spacer(Modifier.width(8.dp));Text("继续未完成的手账")}}
        if (journals.isEmpty()) item {
            Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                EmptyLandscape()
                Text("第一段回忆，从这里开始", style=MaterialTheme.typography.titleLarge)
                Text("选几张照片，写下旅途里让你停下脚步的事。", style=MaterialTheme.typography.bodyLarge)
                Button(onClick=onNew, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("记录我的旅行") }
                OutlinedButton(onClick=onInspiration,modifier=Modifier.fillMaxWidth()) { Text("还没决定去哪？找点旅行灵感") }
                TextButton(onClick=onDemo) { Text("先看看示例手账") }
            }
        }
        items(journals, key={it.id}) { journal -> JournalCard(journal,repo,onClick={onOpen(journal)}) }
        if(journals.isNotEmpty()) item { Text("日子会往前走，回忆在这里停留。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium) }
    }
}

@Composable fun JournalCard(journal: Journal, repo: JournalRepository, reason: String = "", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(journal.photos.isNotEmpty()) LocalPhoto(repo.photoFile(journal.photos.first()),journal.title,Modifier.fillMaxWidth().height(235.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(journal.place.ifBlank{"未填写地点"},color=MaterialTheme.colorScheme.primary, style=MaterialTheme.typography.labelLarge)
            Text(journal.date,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(journal.title,style=MaterialTheme.typography.titleLarge)
        if(journal.body.isNotBlank()) Text(journal.body,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(reason.isNotBlank()) Text(reason,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        TagRow(journal.tags.take(4))
        HorizontalDivider(Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable fun JournalDetail(journal: Journal, repo: JournalRepository, spirit: String, onEdit: () -> Unit,
    onDelete: () -> Unit, onBack: () -> Unit, onError: (String) -> Unit, onNewHere: (() -> Unit)? = null) {
    var delete by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf<String?>(null) }
    var recall by remember(journal.id) { mutableStateOf("") }
    var task by remember { mutableStateOf<Job?>(null) }
    val scope=rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { task?.cancel() } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=40.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Outlined.ArrowBack,"返回") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick=onEdit) { Text("编辑") }
            IconButton(onClick={delete=true}) { Icon(Icons.Outlined.DeleteOutline,"删除手账") }
        } }
        if(journal.photos.isNotEmpty()) item { LocalPhoto(repo.photoFile(journal.photos.first()),journal.title,Modifier.fillMaxWidth().height(300.dp).clickable{fullPhoto=journal.photos.first()}) }
        item { Column(Modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(journal.title,style=MaterialTheme.typography.headlineLarge)
            Text("${journal.date}  ·  ${journal.place.ifBlank{"旅途中的一站"}}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
            journal.mapPoint()?.let{point->Text("地图位置 · ${point.label()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(onNewHere!=null)TextButton(onClick=onNewHere){Text("在这里再写一篇")}}
            TagRow(journal.tags)
            if(journal.body.isNotBlank()) Text(journal.body,style=MaterialTheme.typography.bodyLarge)
        } }
        items(journal.photos.drop(1)) { photo -> LocalPhoto(repo.photoFile(photo),"旅行照片",Modifier.fillMaxWidth().padding(horizontal=24.dp).height(280.dp).clickable{fullPhoto=photo}) }
        if(journal.photoDescription.isNotBlank()) item { Column(Modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(); Text("照片笔记",style=MaterialTheme.typography.titleMedium)
            Text(journal.photoDescription,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Column(Modifier.padding(horizontal=24.dp)) {
            OutlinedButton(enabled=task?.isActive!=true,onClick={ task=scope.launch {
                try { recall=AiClient().recall(withContext(Dispatchers.IO){repo.loadConfig()},journal) }
                catch(e:CancellationException){throw e} catch(e:Exception){onError(e.message?:"暂时无法回顾，请稍后重试")}finally{task=null}
            } }) { Icon(Icons.Outlined.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("请${spirit.ifBlank{"账本精灵"}}一起回顾") }
            if(task?.isActive==true) AiProgress("正在翻阅这一页…"){task?.cancel()}
            if(recall.isNotBlank()) Message(recall)
        } }
    }
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("删除这篇手账？")},text={Text("这条记录和其中的照片会从本机移除。可先在设置里导出备份。")},
        confirmButton={TextButton(onClick={delete=false;onDelete()}){Text("删除")}},dismissButton={TextButton(onClick={delete=false}){Text("保留")}})
    if(fullPhoto!=null) androidx.compose.ui.window.Dialog(onDismissRequest={fullPhoto=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Column {
            TextButton(onClick={fullPhoto=null}){Text("关闭照片")}
            LocalPhoto(repo.photoFile(fullPhoto!!),"完整照片",Modifier.fillMaxSize(),fit=true)
        } }
    }
}

@Composable fun JournalEditor(draft: Journal, repo: JournalRepository, onChange: (Journal) -> Unit, onSave: () -> Unit,
    onBack: () -> Unit, onError: (String) -> Unit, saving: Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var aiTask by remember { mutableStateOf<Job?>(null) }
    var suggestion by remember { mutableStateOf<PhotoSuggestion?>(null) }
    var suggestedTags by remember { mutableStateOf("") }
    var suggestedDescription by remember { mutableStateOf("") }
    var suggestedTitle by remember { mutableStateOf("") }
    var tagsText by remember(draft.id) { mutableStateOf(draft.tags.joinToString("，")) }
    var selectedForAi by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPhoto by remember { mutableStateOf<String?>(null) }
    var mapPicker by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { aiTask?.cancel() } }
    val latestDraft by rememberUpdatedState(draft)
    BackHandler(enabled=importing){onError("照片正在导入，请稍候")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) scope.launch {
            importing=true
            try {
                val room=(12-latestDraft.photos.size).coerceAtLeast(0)
                val imported=withContext(Dispatchers.IO){uris.take(room).mapNotNull{uri->runCatching{repo.importPhoto(uri)}.getOrNull()}}
                val updated=latestDraft.copy(photos=(latestDraft.photos+imported).take(12))
                onChange(updated)
                withContext(Dispatchers.IO){repo.saveDraft(updated)}
                if(imported.size<uris.take(room).size)onError("部分照片无法读取，已保留成功导入的照片")
                if(uris.size>room) onError("每篇最多保存12张照片，已导入可添加的部分")
            } catch(e:Exception){onError(e.message?:"照片导入失败，请重试")}
            finally {importing=false}
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack,enabled=!importing&&!saving){Icon(Icons.Outlined.ArrowBack,"返回")}
            Text("写下这一程",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            Button(onClick=onSave,enabled=!saving&&!importing){Text(if(saving)"保存中" else "保存")}
        } }
        item { OutlinedTextField(draft.title,{onChange(draft.copy(title=it))},label={Text("手账标题")},placeholder={Text("给这段旅程起个名字")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={
                val date=runCatching{LocalDate.parse(draft.date)}.getOrDefault(LocalDate.now())
                DatePickerDialog(context,{_,y,m,d->onChange(latestDraft.copy(date=LocalDate.of(y,m+1,d).toString()))},date.year,date.monthValue-1,date.dayOfMonth).show()
            }){Icon(Icons.Outlined.CalendarToday,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(draft.date)}
        } }
        item { OutlinedTextField(draft.place,{onChange(draft.copy(place=it))},label={Text("地点（可选）")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
        item { Column {
            draft.mapPoint()?.let{Text("已标地图位置 · ${it.label()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Row {
                TextButton(onClick={mapPicker=true},enabled=!saving&&!importing){Icon(Icons.Outlined.Place,null);Spacer(Modifier.width(6.dp));Text(if(draft.mapPoint()==null)"在地图选点" else "更改地图位置")}
                if(draft.mapPoint()!=null)TextButton(onClick={onChange(draft.copy(latitude=null,longitude=null))},enabled=!saving&&!importing){Text("移除标注")}
            }
        } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("旅途照片  ${draft.photos.size}/12",style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!importing&&draft.photos.size<12){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text(if(importing)"导入中" else "添加照片")}
        } }
        if(draft.photos.isEmpty()) item { Text("可以先写文字，随时回来补上照片。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(draft.photos,key={it}) { photo -> Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            LocalPhoto(repo.photoFile(photo),"点击查看照片",Modifier.fillMaxWidth().height(200.dp).clickable{showPhoto=photo})
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                val chosen=photo in selectedForAi
                Checkbox(chosen,onCheckedChange={value->
                    if(value && selectedForAi.size>=4) onError("一次最多选择4张照片交给精灵")
                    else selectedForAi=if(value) selectedForAi+photo else selectedForAi-photo
                })
                Text("交给精灵",style=MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick={onChange(draft.copy(photos=listOf(photo)+draft.photos.filterNot{it==photo}))}){Text(if(draft.photos.first()==photo)"封面" else "设为封面")}
                IconButton(enabled=draft.photos.indexOf(photo)>0,onClick={
                    val p=draft.photos.toMutableList();val i=p.indexOf(photo);if(i>0){p[i]=p[i-1];p[i-1]=photo;onChange(draft.copy(photos=p))}
                }){Icon(Icons.Outlined.ArrowUpward,"前移照片",Modifier.size(20.dp))}
                IconButton(onClick={selectedForAi=selectedForAi-photo;onChange(draft.copy(photos=draft.photos-photo))}){Icon(Icons.Outlined.Close,"移除照片")}
            }
        } }
        item { OutlinedTextField(draft.body,{onChange(draft.copy(body=it))},label={Text("这一路的故事")},placeholder={Text("天气、风景、一次偶遇，或者此刻的心情…")},modifier=Modifier.fillMaxWidth(),minLines=6,maxLines=18) }
        item { OutlinedTextField(tagsText,{tagsText=it;onChange(draft.copy(tags=it.split('，',',','\n').map{tag->tag.trim()}.filter{tag->tag.isNotEmpty()}.take(20)))},label={Text("标签，用逗号分隔")},modifier=Modifier.fillMaxWidth()) }
        if(draft.photoDescription.isNotEmpty()) item { OutlinedTextField(draft.photoDescription,{onChange(draft.copy(photoDescription=it))},label={Text("照片笔记")},modifier=Modifier.fillMaxWidth(),minLines=2) }
        item { Column {
            OutlinedButton(enabled=aiTask?.isActive!=true,onClick={ aiTask=scope.launch {
                try {
                    val selected=latestDraft.photos.filter{it in selectedForAi}
                    if(selected.isEmpty()) {onError("先勾选1～4张照片交给精灵");return@launch}
                    val config=withContext(Dispatchers.IO){repo.loadConfig()}
                    val s=AiClient().describe(config,selected.map{repo.photoFile(it)},"地点：${latestDraft.place}；用户记录：${latestDraft.body}")
                    suggestion=s;suggestedTags=s.tags.joinToString("，");suggestedDescription=s.description;suggestedTitle=s.title
            }catch(e:CancellationException){throw e}catch(e:Exception){onError(e.message?:"识别失败，可以继续手动记录")}finally{aiTask=null}
            } }){Icon(Icons.Outlined.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("让精灵整理选中照片（${selectedForAi.size}张）")}
            if(aiTask?.isActive==true) AiProgress("正在观察照片…"){aiTask?.cancel()}
            Text("生成的标签和文字由你确认后使用。草稿会在本机暂存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
    if(mapPicker)MapLocationPicker(latestDraft.mapPoint(),latestDraft.place,onDismiss={mapPicker=false},onChoose={point,name->onChange(latestDraft.copy(place=name,latitude=point.latitude,longitude=point.longitude));mapPicker=false})
    if(suggestion!=null) AlertDialog(onDismissRequest={suggestion=null},title={Text("精灵的整理建议")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(suggestedTitle,{suggestedTitle=it},label={Text("建议标题")})
        OutlinedTextField(suggestedTags,{suggestedTags=it},label={Text("候选标签")})
        OutlinedTextField(suggestedDescription,{suggestedDescription=it},label={Text("照片笔记")},maxLines=5)
        Text("应用后可继续编辑。标题已有内容时会保留原题。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={val tags=(latestDraft.tags+suggestedTags.split('，',',').map{it.trim()}).filter{it.isNotBlank()}.distinct().take(20);tagsText=tags.joinToString("，");onChange(latestDraft.copy(title=latestDraft.title.ifBlank{suggestedTitle},tags=tags,photoDescription=suggestedDescription));suggestion=null}){Text("确认使用")}},dismissButton={TextButton(onClick={suggestion=null}){Text("暂不使用")}})
    if(showPhoto!=null) androidx.compose.ui.window.Dialog(onDismissRequest={showPhoto=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()){Column{TextButton(onClick={showPhoto=null}){Text("关闭照片")};LocalPhoto(repo.photoFile(showPhoto!!),"原照片",Modifier.fillMaxSize(),fit=true)}}
    }
}
