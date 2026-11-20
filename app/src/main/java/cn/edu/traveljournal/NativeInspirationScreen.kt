package cn.edu.traveljournal

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun NativeInspirationScreen(wishes:List<Wish>,plans:List<TravelPlan>,repo:JournalRepository,onSaveWish:(Wish)->Unit,onDeleteWish:(String)->Unit,onStart:(Wish)->Unit,onSettings:()->Unit,onPlansChanged:()->Unit,onError:(String)->Unit,onEditorChange:(Boolean)->Unit={}) {
    var tab by rememberSaveable{mutableStateOf(0)}
    var editing by rememberSaveable{mutableStateOf<String?>(null)}
    val editorCallback by rememberUpdatedState(onEditorChange)
    LaunchedEffect(editing!=null){editorCallback(editing!=null)}
    DisposableEffect(Unit){onDispose{editorCallback(false)}}
    var draft by remember{mutableStateOf<TravelPlan?>(null)}
    var needs by rememberSaveable{mutableStateOf("")};var origin by rememberSaveable{mutableStateOf("")};var budget by rememberSaveable{mutableStateOf("")};var count by rememberSaveable{mutableStateOf("3")}
    var error by remember{mutableStateOf("")}
    // 草稿冲突时先问一句，而不是悄悄把按钮禁用。
    var pendingPlan by remember{mutableStateOf<TravelPlan?>(null)}
    var deleting by remember{mutableStateOf<TravelPlan?>(null)}
    var deletingWish by remember{mutableStateOf<Wish?>(null)}
    var draftLoaded by remember{mutableStateOf(false)}
    // AI 网页版推荐相关状态。解析成功的原文存进 saveable，旋屏或切页后结果还在。
    var aiWebInput by rememberSaveable{mutableStateOf("")}
    var showAiWeb by rememberSaveable{mutableStateOf(false)}
    var aiService by rememberSaveable{mutableStateOf(AiWebHelper.DEFAULT_SERVICE)}
    var aiWebReply by rememberSaveable{mutableStateOf("")}
    var aiWebError by remember{mutableStateOf("")}
    var aiWebNotice by remember{mutableStateOf("")}
    val aiWebResult:OnlineRecommendations?=remember(aiWebReply){aiWebReply.takeIf{it.isNotBlank()}?.let{runCatching{AiWebHelper.parseAiResponse(it,needs)}.getOrNull()}}
    val scope=rememberCoroutineScope();val context=LocalContext.current
    // 城市索引较大，放到后台线程加载；加载失败只是不自动填省份，不影响其它功能。
    var atlas by remember{mutableStateOf<CityAtlas?>(null)}
    LaunchedEffect(context){atlas=withContext(Dispatchers.IO){runCatching{CityAtlas.load(context)}.getOrNull()}}
    val wishQuery=buildString{append(needs.trim());if(origin.isNotBlank())append("；出发地：").append(origin.trim());append("；天数：").append(count);if(budget.isNotBlank())append("；预算：").append(budget.trim())}
    LaunchedEffect(repo){try{draft=withContext(Dispatchers.IO){repo.loadPlanDraft()};draftLoaded=true}catch(e:Exception){onError(e.message?:"读取草稿失败")}}
    fun start(plan:TravelPlan){
        if(!draftLoaded){error="草稿尚未读取完成，请稍后重试";return}
        val existing=draft
        if(existing!=null&&existing.id!=plan.id){pendingPlan=plan;return}
        editing=planJson(nativePlanForOpen(plan,existing)).toString()
    }
    // 把目的地转成计划：顺手带上能识别出的省份，省得用户再选一次。
    fun planFromDestination(d:Destination):TravelPlan=TravelPlan(destination=d.name.trim(),origin=origin.trim(),budget=budget.trim(),provinceCode=atlas?.provinceCodeFor(d.name,d.region))
    if(editing!=null){
        val plan=remember(editing){planFromJson(JSONObject(editing!!))}
        NativePlanEditor(plan,repo,onChange={editing=planJson(it).toString()},onBack={draft=it;editing=null},onSaved={draft=null;editing=null;onPlansChanged()},onError=onError)
        return
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageTitle("下一程，慢慢计划","把一点向往，写成出发的日子。")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("找下一站","我的计划").forEachIndexed{i,title->FilterChip(tab==i,onClick={tab=i},label={Text(title)})}}}
        draft?.let{p->item{OutlinedButton(onClick={start(p)},modifier=Modifier.fillMaxWidth()){Text("继续未完成的旅行计划")}}}
        if(error.isNotBlank())item{Message(error,true);TextButton(onClick=onSettings){Text("检查 AI 设置")}}
        if(tab==1){
            item{Button(onClick={start(TravelPlan())}){Text("自己写计划")}}
            if(plans.isEmpty())item{Message("给下一程留一页。没有网络也可以手写计划。")}
            items(plans,key={it.id}){p->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(p.title.ifBlank{p.destination},style=MaterialTheme.typography.titleLarge);Text("${p.destination} · ${p.days.size} 天 · ${p.startDate.ifBlank{"日期待定"}}")
                Row{TextButton(onClick={start(p)}){Text("打开计划")};TextButton(onClick={deleting=p}){Text("删除")}}}}}
            // 旧版「想去清单」的数据不删也不丢：迁到这里，用户想转的时候再转成计划。
            if(wishes.isNotEmpty()){
                item{Text("旧版收藏的目的地",style=MaterialTheme.typography.titleLarge);Text("这些是从「想去清单」过来的，直接转成计划就能继续写。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                items(wishes,key={it.id}){wish->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(wish.destination.name,style=MaterialTheme.typography.titleLarge)
                    if(wish.destination.region.isNotBlank())Text(wish.destination.region,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(wish.reason.isNotBlank())Text(wish.reason)
                    Row{TextButton(onClick={val code=atlas?.provinceCodeFor(wish.destination.name,wish.destination.region);start(TravelPlan(destination=wish.destination.name,provinceCode=code))}){Text("转为计划")};TextButton(onClick={deletingWish=wish}){Text("移除")}}
                }}}
            }
        }else{
            item{OutlinedTextField(needs,{needs=it},label={Text("这次想怎样旅行？")},modifier=Modifier.fillMaxWidth(),minLines=3)}
            item{OutlinedTextField(origin,{origin=it},label={Text("出发地（选填）")},modifier=Modifier.fillMaxWidth())}
            item{OutlinedTextField(count,{count=it},label={Text("旅行天数 · 1–30")},modifier=Modifier.fillMaxWidth())}
            item{OutlinedTextField(budget,{budget=it},label={Text("预算（选填，可写币种）")},modifier=Modifier.fillMaxWidth())}
            // AI 网页版推荐入口：不需要 API Key，走「提示词 → 网页 AI → 粘回 JSON」的流程
            item{OutlinedButton(onClick={showAiWeb=!showAiWeb;if(showAiWeb)aiWebError=""},modifier=Modifier.fillMaxWidth()){Text(if(showAiWeb)"收起「AI 网页版推荐」" else "用 AI 网页版推荐（免费，无需 API）")}}
            if(showAiWeb){
                item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("不用配置 API：把需求交给网页版 AI，再把它的回复粘回来。",style=MaterialTheme.typography.bodyMedium)
                    Text("① 选择 AI 网页",style=MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){AiWebHelper.services.forEach{s->FilterChip(aiService==s.id,onClick={aiService=s.id},label={Text(s.label)})}}
                    Button(enabled=needs.isNotBlank(),onClick={
                        val prompt=AiWebHelper.generatePrompt(needs,origin,count,budget)
                        runCatching{AiWebHelper.copyAndOpenAi(context,prompt,aiService)}
                            .onSuccess{aiWebError="";aiWebNotice="提示词已复制。在 ${AiWebHelper.serviceLabel(aiService)} 里粘贴发送，再把整段回复复制回来。"}
                            .onFailure{aiWebNotice="";aiWebError="打不开 ${AiWebHelper.serviceLabel(aiService)}，请手动打开网页；提示词已经在剪贴板里了。"}
                    },modifier=Modifier.fillMaxWidth()){Text("生成提示词并打开 ${AiWebHelper.serviceLabel(aiService)}")}
                    if(needs.isBlank())Text("先在上方写一句「这次想怎样旅行」，才能生成提示词。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("② 把 AI 的回复粘回来",style=MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={
                            val text=AiWebHelper.clipboardText(context)
                            when{
                                text.isBlank()->{aiWebNotice="";aiWebError="剪贴板是空的，先在 AI 网页里复制它的回复"}
                                AiWebHelper.isAiResponse(text)->{aiWebInput=text;aiWebError="";aiWebNotice="已从剪贴板读到 AI 回复，点「解析回复」"}
                                else->{aiWebInput=text;aiWebError="";aiWebNotice="已填入剪贴板内容；如果它不是 JSON，解析会失败"}
                            }
                        },modifier=Modifier.weight(1f)){Text("从剪贴板读取")}
                        OutlinedButton(onClick={aiWebInput="";aiWebReply="";aiWebError="";aiWebNotice=""},modifier=Modifier.weight(1f)){Text("清空")}
                    }
                    OutlinedTextField(aiWebInput,{aiWebInput=it;if(aiWebError.isNotBlank())aiWebError=""},label={Text("粘贴 AI 的回复")},placeholder={Text("整段 JSON 即可，前后的说明文字不影响解析")},modifier=Modifier.fillMaxWidth(),minLines=5,maxLines=12)
                    Button(enabled=aiWebInput.isNotBlank(),onClick={
                        val text=aiWebInput.trim()
                        try{
                            val parsed=AiWebHelper.parseAiResponse(text,needs)
                            aiWebReply=text;aiWebError=""
                            aiWebNotice=if(parsed.destinations.isEmpty())"解析成功，但 AI 没有给出有效地点，换个问法再试" else "解析成功，共 ${parsed.destinations.size} 个去处"
                        }catch(e:AiException){aiWebReply="";aiWebNotice="";aiWebError=e.message?:"解析失败"}
                    },modifier=Modifier.fillMaxWidth()){Text("解析回复")}
                    if(aiWebNotice.isNotBlank())Message(aiWebNotice)
                    if(aiWebError.isNotBlank())Message(aiWebError,true)
                }}}
            }
            // AI 网页版推荐结果
            aiWebResult?.let{aw->
                item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("AI 网页版推荐",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                    if(aw.destinations.isNotEmpty())Text("${aw.destinations.size} 个去处",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
                if(aw.result.understood.isNotBlank())item{Message(aw.result.understood)}
                if(aw.destinations.isEmpty())item{Message("AI 未返回有效推荐。可以让它「只返回 JSON」再试一次。")}
                items(aw.destinations,key={it.id}){d->
                    val rec=aw.result.items.find{it.id==d.id}
                    val meta=listOfNotNull(d.region.takeIf{it.isNotBlank()},d.days.takeIf{it.isNotBlank()}?.let{"建议停留 $it"}).joinToString(" · ")
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(d.name,style=MaterialTheme.typography.titleLarge)
                        if(meta.isNotBlank())Text(meta,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(rec?.reason?:d.summary)
                        rec?.caution?.takeIf{it.isNotBlank()}?.let{Text("注意：$it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            if(d.sourceUrl.isNotBlank())TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(d.sourceUrl)))}.onFailure{onError("无法打开来源链接")}}){Text("查看资料来源")}
                        }
                        Button(onClick={start(planFromDestination(d))},modifier=Modifier.fillMaxWidth()){Text("用它开始写计划")}
                        if(draft!=null)Text("还有一份未完成的草稿，点开后可以选「继续草稿」或「放弃草稿并新建」。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }}
                }
            }
            item{OutlinedButton(onClick={start(TravelPlan(origin=origin,budget=budget))}){Text("已经决定了，自己写计划")}}
            item{TextButton(onClick=onSettings){Text("接入 / 修改 AI")};Text("推荐来自你在浏览器里搜到的 AI 回复：复制回复，回到这里粘贴整理。出发前请核对交通、开放和预订信息。",style=MaterialTheme.typography.bodySmall)}
        }
    }
    pendingPlan?.let{target->AlertDialog(onDismissRequest={pendingPlan=null},
        title={Text("还有一份没保存的草稿")},
        text={Text("「${draft?.destination?.takeIf{it.isNotBlank()}?:"未命名计划"}」还没存进手账。可以继续那份草稿，也可以放弃它，直接开始写「${target.destination.ifBlank{"新计划"}}」。")},
        confirmButton={TextButton(onClick={val d=draft;pendingPlan=null;if(d!=null)start(d)}){Text("继续那份草稿")}},
        dismissButton={TextButton(onClick={val t=target;pendingPlan=null;draft=null;scope.launch{runCatching{withContext(Dispatchers.IO){repo.clearPlanDraft()}}.onFailure{onError("草稿未能清除，请在计划页继续它")}};start(t)}){Text("放弃草稿并新建")}})}
    deleting?.let{p->AlertDialog(onDismissRequest={deleting=null},title={Text("删除这份旅行计划？")},text={Text("手账记录会保留。")},confirmButton={TextButton(onClick={deleting=null;scope.launch{try{withContext(Dispatchers.IO){repo.deletePlan(p.id)};if(draft?.id==p.id)draft=null;onPlansChanged()}catch(e:Exception){onError(e.message?:"删除失败")}}}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("保留")}})}
    deletingWish?.let{wish->AlertDialog(onDismissRequest={deletingWish=null},title={Text("移除这份期待？")},text={Text("关联的手账会保留。")},confirmButton={TextButton(onClick={deletingWish=null;onDeleteWish(wish.id)}){Text("移除")}},dismissButton={TextButton(onClick={deletingWish=null}){Text("保留")}})}
}

/** Opening a saved row must never replace a newer unsaved revision of that row. */
internal fun nativePlanForOpen(saved:TravelPlan,draft:TravelPlan?):TravelPlan=if(draft?.id==saved.id)draft else saved

@Composable private fun NativeWishContent(wish:Wish,onSave:(Wish)->Unit,onStart:()->Unit,onDelete:()->Unit,onError:(String)->Unit){
    var notes by rememberSaveable(wish.id,wish.notes){mutableStateOf(wish.notes)}
    val context=LocalContext.current
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(wish.destination.name,style=MaterialTheme.typography.titleLarge);Text(wish.reason)
        OutlinedTextField(notes,{notes=it},label={Text("给这份期待留点备注")},modifier=Modifier.fillMaxWidth())
        TextButton(onClick={onSave(wish.copy(notes=notes))}){Text("保存备注")}
        Row{Checkbox(wish.visited,{onSave(wish.copy(visited=it))});Text("我已经去过这里")}
        TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(wish.destination.sourceUrl)))}.onFailure{onError("无法打开来源链接")}}){Text("查看资料来源")}
        Row{TextButton(onClick=onStart){Text("开始记录")};TextButton(onClick=onDelete){Text("移除收藏")}}
    }}
}

