package cn.edu.traveljournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import org.json.JSONObject

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=true
        WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightNavigationBars=android.os.Build.VERSION.SDK_INT >= 27
        setContent{TravelTheme{TravelApp()}}
    }
}

@Composable fun TravelApp(){
    val context=LocalContext.current
    val repo=remember{JournalRepository(context.applicationContext)}
    val scope=rememberCoroutineScope()
    val snackbar=remember{SnackbarHostState()}
    var journals by remember{mutableStateOf<List<Journal>>(emptyList())}
    var wishes by remember{mutableStateOf<List<Wish>>(emptyList())}
    var config by remember{mutableStateOf(AiConfig())}
    var loading by remember{mutableStateOf(true)}
    var loadError by remember{mutableStateOf("")}
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var archive by rememberSaveable{mutableStateOf(false)}
    var detailId by rememberSaveable{mutableStateOf<String?>(null)}
    val draftSaver=remember { Saver<Journal?,String>(save={it?.let{j->journalJson(j).toString()}.orEmpty()},restore={text->text.takeIf{it.isNotBlank()}?.let{runCatching{journalFromJson(JSONObject(it))}.getOrNull()}}) }
    var draft by rememberSaveable(stateSaver=draftSaver){mutableStateOf<Journal?>(null)}
    var recovered by remember{mutableStateOf<Journal?>(null)}
    var parkedDraft by remember{mutableStateOf<Journal?>(null)}
    var leave by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var mapRevision by remember{mutableIntStateOf(0)}
    val mapMoves=remember{Mutex()}
    val stateHolder=rememberSaveableStateHolder()
    fun message(s:String){scope.launch{snackbar.showSnackbar(s)}}
    suspend fun refresh(){
        withContext(Dispatchers.IO){repo.journals() to repo.wishes()}.let{journals=it.first;wishes=it.second}
        config=try{withContext(Dispatchers.IO){repo.loadConfig()}}catch(e:Exception){message("AI密钥暂时无法读取，可在设置中重新填写；本地手账可继续使用");AiConfig()}
        if(draft==null){parkedDraft=withContext(Dispatchers.IO){repo.loadDraft()};if(recovered!=null)recovered=parkedDraft}
    }
    LaunchedEffect(Unit){
        try{refresh();if(draft==null){recovered=withContext(Dispatchers.IO){repo.loadDraft()};parkedDraft=recovered}}
        catch(e:Exception){loadError=e.message?:"本地内容暂时无法载入"}
        finally{loading=false}
    }
    LaunchedEffect(draft,saving){
        val current=draft
        if(current!=null&&!saving){delay(500);try{withContext(Dispatchers.IO){repo.saveDraft(current)}}catch(e:CancellationException){throw e}catch(e:Exception){message("草稿暂存失败，请及时保存")}}
    }
    fun create(j:Journal=Journal()){if(saving)return;if(parkedDraft!=null){recovered=parkedDraft;return};detailId=null;draft=j}
    fun save(){
        if(saving)return
        val j=draft?:return
        when{
            j.title.isBlank()->{message("请给这篇手账填写一个标题");return}
            j.body.isBlank()&&j.photos.isEmpty()->{message("写点文字或添加一张照片再保存");return}
            runCatching{LocalDate.parse(j.date)}.isFailure->{message("请选择有效的旅行日期");return}
        }
        saving=true
        scope.launch{
            try{
                withContext(Dispatchers.IO){
                    repo.saveJournal(j.copy(title=j.title.trim(),updatedAt=System.currentTimeMillis()))
                    repo.clearDraft()
                }
                refresh();draft=null;parkedDraft=null;detailId=j.id;message("已把这一程留在手账里")
            }catch(e:Exception){message(e.message?:"保存失败，请重试，草稿仍保留")}
            finally{saving=false}
        }
    }
    BackHandler(enabled=saving||draft!=null||detailId!=null||tab!=0||archive){if(!saving)when{draft!=null->leave=true;detailId!=null->detailId=null;archive->archive=false;else->tab=0}}
    val detail=journals.find{it.id==detailId}
    val labels=listOf("地图","搜索","灵感","设置")
    val icons=listOf(Icons.Outlined.Map,Icons.Outlined.Search,Icons.Outlined.Explore,Icons.Outlined.Tune)
    BoxWithConstraints{
        val wide=maxWidth>=720.dp
        Scaffold(snackbarHost={SnackbarHost(snackbar)},
            bottomBar={if(!wide&&draft==null&&detail==null&&!archive)NavigationBar(containerColor=MaterialTheme.colorScheme.surface){labels.forEachIndexed{i,label->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.secondaryContainer))}}}
        ){padding->
            Row(Modifier.fillMaxSize().padding(padding)){
                if(wide&&draft==null&&detail==null&&!archive)NavigationRail{labels.forEachIndexed{i,label->NavigationRailItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label)})}}
                Box(Modifier.weight(1f).fillMaxHeight()){
                    when{
                        loading->Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Spacer(Modifier.height(16.dp));Text("正在翻开你的旅行手账…")}
                        loadError.isNotBlank()->Column(Modifier.padding(24.dp)){Message(loadError,true);Button(onClick={scope.launch{try{refresh();loadError=""}catch(e:Exception){message(e.message?:"载入失败")}}}){Text("重试载入")}}
                        draft!=null->JournalEditor(draft!!,repo,{if(!saving)draft=it},::save,{if(!saving)leave=true},::message,saving)
                        detail!=null->JournalDetail(detail,repo,config.spiritName,onEdit={create(detail)},onDelete={scope.launch{try{withContext(Dispatchers.IO){repo.deleteJournal(detail.id)};refresh();detailId=null;message("手账已删除")}catch(e:Exception){message(e.message?:"删除失败")}}},onBack={detailId=null},onError=::message,onNewHere={create(Journal(place=detail.place,latitude=detail.latitude,longitude=detail.longitude,visited=detail.visited))})
                        archive&&tab==0->Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={archive=false}){Text("返回地图")};Spacer(Modifier.weight(1f));TextButton(onClick={create()}){Text("新建手账")}}
                            Box(Modifier.weight(1f)){JournalHome(journals,repo,{create()},{detailId=it.id},onDemo={scope.launch{try{withContext(Dispatchers.IO){loadDemoJournals(context,repo)};refresh()}catch(e:Exception){message(e.message?:"示例载入失败")}}},onInspiration={archive=false;tab=2},hasDraft=parkedDraft!=null,onResume={recovered=parkedDraft})}
                        }
                        else->stateHolder.SaveableStateProvider(tab){when(tab){
                            0->MapHome(journals,repo,::create,{detailId=it.id},{archive=true},onDemo={scope.launch{try{withContext(Dispatchers.IO){loadDemoJournals(context,repo)};refresh();message("已添加3篇教学示例，地图坐标仅作演示")}catch(e:Exception){message(e.message?:"示例载入失败")}}},hasDraft=parkedDraft!=null,onResume={recovered=parkedDraft},onError=::message,mapRevision=mapRevision,onMoveRecord={id,p->scope.launch{mapMoves.withLock{try{withContext(Dispatchers.IO){repo.moveMapSticker(id,p)};refresh()}catch(e:CancellationException){throw e}catch(e:Exception){message(e.message?:"位置保存失败，请重新拖动")}finally{mapRevision++}}}})
                            1->SearchScreen(journals,repo,{detailId=it.id},{tab=3})
                            2->InspirationScreen(wishes,repo,onSaveWish={w->scope.launch{try{withContext(Dispatchers.IO){repo.saveWish(w)};refresh();message("已保存到想去清单")}catch(e:Exception){message(e.message?:"收藏失败")}}},onDeleteWish={id->scope.launch{try{withContext(Dispatchers.IO){repo.deleteWish(id)};refresh();message("已移除收藏")}catch(e:Exception){message(e.message?:"移除失败")}}},onStart={wish->create(Journal(place=wish.destination.name,title="${wish.destination.name}旅行",wishId=wish.id,visited=false))},onSettings={tab=3})
                            3->SettingsScreen(config,repo,onSave={c->scope.launch{try{withContext(Dispatchers.IO){repo.saveConfig(c)};refresh();message("设置已保存")}catch(e:Exception){message(e.message?:"设置保存失败")}}},onImported={scope.launch{refresh()}},onMessage=::message)
                        }}
                    }
                }
            }
        }
    }
    if(leave)AlertDialog(onDismissRequest={leave=false},title={Text("离开这一页？")},text={Text("可以保存为手账、保留本机草稿，或放弃这次编辑。")},confirmButton={TextButton(onClick={leave=false;save()},enabled=!saving){Text("保存手账")}},dismissButton={Row{
        TextButton(onClick={if(!saving){val d=draft;saving=true;leave=false;scope.launch{try{withContext(Dispatchers.IO){if(d!=null)repo.saveDraft(d)};parkedDraft=d;draft=null;message("草稿已留在本机，可从首页继续")}catch(e:Exception){message("草稿保存失败，请先保存手账")}finally{saving=false}}}}){Text("保留草稿")}
        TextButton(onClick={if(!saving){saving=true;leave=false;scope.launch{try{withContext(Dispatchers.IO){repo.clearDraft()};draft=null}catch(e:Exception){message("清理草稿失败，请稍后重试")}finally{saving=false}}}}){Text("放弃")}
    }})
    if(recovered!=null)AlertDialog(onDismissRequest={if(!saving)recovered=null},title={Text("还有一页没写完")},text={Text("发现本机暂存的旅行草稿，要继续记录吗？新建另一篇前需要先保存或明确放弃它。")},confirmButton={TextButton(onClick={if(!saving){draft=recovered;parkedDraft=null;recovered=null}},enabled=!saving){Text("继续记录")}},dismissButton={TextButton(onClick={if(!saving){saving=true;scope.launch{try{withContext(Dispatchers.IO){repo.clearDraft()};recovered=null;parkedDraft=null}catch(e:Exception){message("清理草稿失败，原草稿仍保留")}finally{saving=false}}}},enabled=!saving){Text("放弃草稿")}})
    if(saving) androidx.compose.ui.window.Dialog(onDismissRequest={}) {
        Surface(shape=MaterialTheme.shapes.medium,tonalElevation=6.dp){Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp);Text("正在保存本机内容…",style=MaterialTheme.typography.bodyLarge)
        }}
    }
}
