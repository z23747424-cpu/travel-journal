package cn.edu.traveljournal

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable internal fun NativePlanEditor(plan:TravelPlan,repo:JournalRepository,onChange:(TravelPlan)->Unit,onBack:(TravelPlan)->Unit,onSaved:()->Unit,onError:(String)->Unit){
    val scope=rememberCoroutineScope();val context=LocalContext.current
    var busy by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf(false)};var provinces by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var discard by remember{mutableStateOf(false)}
    var autosave by remember{mutableStateOf<Job?>(null)}
    // 用户手动改过省份（含选「不关联」）后，就不再自动覆盖。
    var provinceTouched by remember(plan.id){mutableStateOf(plan.provinceCode!=null)}
    // 区县选填：只决定在省图上插在哪一格，不影响省旗。用户自己选过就不再自动改。
    var countyTouched by remember(plan.id){mutableStateOf(plan.countyCode!=null)}
    var countyDialog by remember{mutableStateOf(false)}
    var countyIndex by remember{mutableStateOf<List<CountyRef>>(emptyList())}
    val atlas=remember{CityAtlas.load(context)}
    // Freeze the exact revision being persisted until the operation succeeds or fails.
    fun change(p:TravelPlan){if(busy)return;onChange(p.copy(updatedAt=System.currentTimeMillis()));message=""}
    fun leave(){if(busy)return;busy=true;scope.launch{try{autosave?.cancelAndJoin();withContext(Dispatchers.IO){repo.savePlanDraft(plan)};onBack(plan)}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"草稿保存失败，请重试";onError(message)}finally{busy=false}}}
    BackHandler{leave()}
    // Serial writes are cancelled before each newer version; repository writes themselves are atomic.
    LaunchedEffect(plan,busy){if(busy)return@LaunchedEffect;autosave=currentCoroutineContext()[Job];delay(350);try{withContext(Dispatchers.IO){repo.savePlanDraft(plan)}}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"草稿保存失败"}}
    fun dayChange(index:Int,day:PlanDay){change(plan.copy(days=plan.days.toMutableList().also{it[index]=day}))}
    LaunchedEffect(plan.provinceCode){
        countyIndex=if(plan.provinceCode==null)emptyList() else withContext(Dispatchers.IO){runCatching{CountyAtlas.index(context).filter{it.provinceCode==plan.provinceCode}}.getOrDefault(emptyList())}
    }
    // 目的地里写了区县名就自动带上，同名的县优先同省的那个。
    LaunchedEffect(plan.provinceCode,plan.destination){
        if(!countyTouched&&plan.countyCode==null&&plan.provinceCode!=null&&plan.destination.trim().length>=2){
            countyRefForText(CountyAtlas.index(context),plan.provinceCode,plan.destination,plan.title)?.let{ref->change(plan.copy(countyCode=ref.code,countyName=ref.name))}
        }
    }
    // 手动输入目的地时也自动关联省份，减少一次点击；猜不出就保持「不关联」。
    LaunchedEffect(plan.destination){
        if(!provinceTouched&&plan.provinceCode==null&&plan.destination.trim().length>=2){
            atlas.provinceCodeFor(plan.destination)?.let{code->change(plan.copy(provinceCode=code))}
        }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().testTag("plan-editor"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{TextButton(onClick={leave()},enabled=!busy){Text("返回并保留草稿")};PageTitle("写下下一程","计划不会把省份标为已去。")}
        if(message.isNotBlank())item{Message(message,true)}
        item{NativePlanField("目的地（必填）",plan.destination){change(plan.copy(destination=it.take(160)))}}
        item{NativePlanField("标题（选填）",plan.title){change(plan.copy(title=it.take(200)))}}
        item{NativePlanField("出发地（选填）",plan.origin){change(plan.copy(origin=it.take(200)))}}
        item{NativePlanField("出发日期（选填，YYYY-MM-DD）",plan.startDate){change(plan.copy(startDate=it.take(10)))}}
        item{NativePlanField("预算（选填）",plan.budget){change(plan.copy(budget=it.take(200)))}}
        item{NativePlanField("行程摘要",plan.summary){change(plan.copy(summary=it.take(20000)))}}
        item{OutlinedButton(onClick={provinces=true}){Text("关联省份：${atlas.provinceAreas.find{it.code==plan.provinceCode}?.name?:"不关联（海外可留空）"}")}}
        item{OutlinedButton(enabled=plan.provinceCode!=null,onClick={countyDialog=true}){Text("关联区县：${if(plan.countyCode!=null) plan.countyName.ifBlank{plan.countyCode} else "不关联（选填）"}")}}
        if(plan.provinceCode!=null&&countyIndex.isEmpty())item{Text("这个省份暂没有可选的区县。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{Row{Checkbox(plan.status=="planned",{change(plan.copy(status=if(it)"planned" else "draft"))});Text("作为出行计划在所选省份插旗")}}
        plan.days.forEachIndexed{index,day->
            item(key=day.id){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("第 ${index+1} 天",style=MaterialTheme.typography.titleLarge)
                NativePlanField("当天主题",day.title){dayChange(index,day.copy(title=it.take(200)))}
                Row{
                    TextButton(enabled=index>0,onClick={val days=plan.days.toMutableList();days[index]=days[index-1].also{days[index-1]=day};change(plan.copy(days=days))}){Text("上移")}
                    TextButton(enabled=index<plan.days.lastIndex,onClick={val days=plan.days.toMutableList();days[index]=days[index+1].also{days[index+1]=day};change(plan.copy(days=days))}){Text("下移")}
                    TextButton(enabled=plan.days.size>1,onClick={change(plan.copy(days=plan.days.filterNot{it.id==day.id}))}){Text("删除当天")}
                }
                day.activities.forEachIndexed{aIndex,a->
                    HorizontalDivider();Text("安排 ${aIndex+1}",style=MaterialTheme.typography.titleSmall)
                    fun update(updated:PlanActivity){dayChange(index,day.copy(activities=day.activities.toMutableList().also{it[aIndex]=updated}))}
                    NativePlanField("时间（可留空）",a.time){update(a.copy(time=it.take(200)))}
                    NativePlanField("地点",a.place){update(a.copy(place=it.take(200)))}
                    NativePlanField("安排说明",a.description){update(a.copy(description=it.take(20000)))}
                    NativePlanField("交通与待确认事项",a.transport){update(a.copy(transport=it.take(200)))}
                    a.sourceIds.forEach{id->plan.sources.find{it.id==id}?.let{s->TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(s.url)))}.onFailure{onError("无法打开来源链接")}}){Text("来源：${s.title}")}}}
                    Row{
                        TextButton(enabled=aIndex>0,onClick={val list=day.activities.toMutableList();list[aIndex]=list[aIndex-1].also{list[aIndex-1]=a};dayChange(index,day.copy(activities=list))}){Text("上移")}
                        TextButton(enabled=aIndex<day.activities.lastIndex,onClick={val list=day.activities.toMutableList();list[aIndex]=list[aIndex+1].also{list[aIndex+1]=a};dayChange(index,day.copy(activities=list))}){Text("下移")}
                        TextButton(onClick={dayChange(index,day.copy(activities=day.activities.filterNot{it.id==a.id}))}){Text("删除安排")}
                    }
                }
                OutlinedButton(enabled=day.activities.size<20,onClick={dayChange(index,day.copy(activities=day.activities+PlanActivity()))}){Text("添加安排")}
            }}}
        }
        item{OutlinedButton(enabled=plan.days.size<30,onClick={change(plan.copy(days=plan.days+PlanDay()))}){Text("添加一天 · ${plan.days.size}/30")}}
        item{NativePlanField("旅行备注",plan.notes){change(plan.copy(notes=it.take(20000)))}}
        if(plan.aiGenerated)item{Message("这是基于网页资料的初稿。来源一致性检查不代表事实核验，请自行确认后保存。")}
        item{Button(enabled=!busy,onClick={try{validateTravelPlan(plan);confirm=true}catch(e:Exception){message=e.message?:"请检查计划内容"}},modifier=Modifier.fillMaxWidth()){Text("保存计划")}}
        item{TextButton(enabled=!busy,onClick={discard=true},modifier=Modifier.fillMaxWidth()){Text("放弃未保存的修改")}}
    }
    if(provinces)AlertDialog(onDismissRequest={provinces=false},title={Text("由你确认关联省份")},text={LazyColumn(Modifier.heightIn(max=360.dp)){item{TextButton(onClick={provinceTouched=true;change(plan.copy(provinceCode=null,countyCode=null,countyName=""));provinces=false}){Text("不关联省份")}};items(atlas.provinceAreas.size){i->val p=atlas.provinceAreas[i];TextButton(onClick={provinceTouched=true;change(plan.copy(provinceCode=p.code,countyCode=null,countyName=""));provinces=false},modifier=Modifier.fillMaxWidth()){Text(p.name)}}}},confirmButton={TextButton(onClick={provinces=false}){Text("取消")}})
    if(countyDialog)AlertDialog(onDismissRequest={countyDialog=false},title={Text("由你确认关联区县（选填）")},text={LazyColumn(Modifier.heightIn(max=360.dp)){item{TextButton(onClick={countyTouched=true;change(plan.copy(countyCode=null,countyName=""));countyDialog=false}){Text("不关联区县")}};items(countyIndex.size){i->val c=countyIndex[i];TextButton(onClick={countyTouched=true;change(plan.copy(countyCode=c.code,countyName=c.name));countyDialog=false},modifier=Modifier.fillMaxWidth()){Text(c.name)}}}},confirmButton={TextButton(onClick={countyDialog=false}){Text("取消")}})
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("保存这份旅行计划？")},text={Text("${plan.destination} · ${plan.days.size} 天。之后仍可修改。")},confirmButton={TextButton(enabled=!busy,onClick={confirm=false;busy=true;scope.launch{try{autosave?.cancelAndJoin();withContext(Dispatchers.IO){repo.savePlan(plan)};onSaved()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"保存失败，草稿仍保留";onError(message)}finally{busy=false}}}){Text("确认保存")}},dismissButton={TextButton(onClick={confirm=false}){Text("继续编辑")}})
    if(discard)AlertDialog(onDismissRequest={if(!busy)discard=false},title={Text("放弃未保存的修改？")},text={Text("仅清除这份草稿，已保存的计划仍会保留。")},confirmButton={TextButton(enabled=!busy,onClick={busy=true;scope.launch{try{autosave?.cancelAndJoin();withContext(Dispatchers.IO){repo.clearPlanDraft()};discard=false;onSaved()}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"草稿清除失败，修改仍保留";onError(message)}finally{busy=false}}}){Text("确认放弃")}},dismissButton={TextButton(enabled=!busy,onClick={discard=false}){Text("继续编辑")}})
    if(busy)AlertDialog(onDismissRequest={},title={Text("正在保存修改")},text={Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(24.dp));Text("请稍候…")}},confirmButton={})
}

@Composable private fun NativePlanField(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth())}
