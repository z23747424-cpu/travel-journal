package cn.edu.traveljournal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable fun SettingsScreen(config:AiConfig,repo:JournalRepository,onSave:(AiConfig)->Unit,onImported:()->Unit,onMessage:(String)->Unit,enterAtMaxZoom:Boolean=true,onEnterAtMaxZoom:(Boolean)->Unit={}) {
    var name by rememberSaveable(config.spiritName){mutableStateOf(config.spiritName)}
    var endpoint by rememberSaveable(config.baseUrl){mutableStateOf(config.baseUrl)}
    var model by rememberSaveable(config.model){mutableStateOf(config.model)}
    // The credential is deliberately excluded from saved instance state and exports.
    var key by remember(config.apiKey){mutableStateOf(config.apiKey)}
    var visible by remember{mutableStateOf(false)}
    var consent by rememberSaveable(config.consent){mutableStateOf(config.consent)}
    var result by remember{mutableStateOf("")}
    var resultError by remember{mutableStateOf(false)}
    var task by remember{mutableStateOf<Job?>(null)}
    var archiveBusy by remember{mutableStateOf(false)}
    var confirmClear by remember{mutableStateOf(false)}
    var showLicenses by remember{mutableStateOf(false)}
    var importUri by remember{mutableStateOf<android.net.Uri?>(null)}
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    val versionName=remember{runCatching{context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull().orEmpty()}
    if(showLicenses) {
        val lucide=remember { context.assets.open("licenses/lucide-LICENSE.txt").bufferedReader().use { it.readText() } }
        val fontLicense=remember { runCatching { context.assets.open("licenses/MaShanZheng-OFL.txt").bufferedReader().use { it.readText() } }.getOrDefault("") }
        AlertDialog(onDismissRequest={showLicenses=false},title={Text("开源许可")},text={
            Text(lucide + "\n\n———\n\n手写字体 Ma Shan Zheng（SIL Open Font License 1.1）\n\n" + fontLicense,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)
        },confirmButton={TextButton(onClick={showLicenses=false}){Text("关闭")}})
    }
    fun current()=AiConfig(endpoint.trim(),key.trim(),model.trim(),name.trim().take(30),consent)
    DisposableEffect(Unit){onDispose{task?.cancel()}}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null) scope.launch{
            archiveBusy=true
            try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{repo.exportBackup(it)}?:error("无法打开备份文件")};onMessage("备份已导出，包含手账、照片、计划与省份设计")}
            catch(e:Exception){onMessage(e.message?:"备份导出失败，请重试")}
            finally{archiveBusy=false}
        }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->importUri=uri}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item{PageTitle("陪你记录的精灵", "给它一个名字，再接入你自己的 AI。")}
        item{OutlinedTextField(name,{name=it.take(30)},label={Text("精灵的名字")},placeholder={Text("由你来取，留空称为账本精灵")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{HorizontalDivider();Spacer(Modifier.height(18.dp));Text("连接你的 AI",style=MaterialTheme.typography.titleLarge)}
        item{Text("支持兼容 Chat Completions 的服务。照片整理需要支持图片输入的模型。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{OutlinedTextField(endpoint,{endpoint=it;if(it.trim()!=config.baseUrl)consent=false},label={Text("服务地址（HTTPS）")},placeholder={Text("https://服务域名/v1")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{OutlinedTextField(key,{key=it},label={Text("API 密钥")},modifier=Modifier.fillMaxWidth(),singleLine=true,
            visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible)"隐藏密钥" else "显示密钥")}})}
        item{OutlinedTextField(model,{model=it},label={Text("模型名称")},placeholder={Text("填写服务商提供的模型标识")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(verticalAlignment=Alignment.Top){Checkbox(consent,{consent=it});Text("我了解：使用精灵时，选定照片或文字会发送给配置的 AI 服务商；灵感页的网页版推荐由我在浏览器中自行搜索，应用只整理我粘贴回来的内容，不再调用任何联网搜索接口。调用可能产生费用。地图在本机绘制，不请求在线底图。",modifier=Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodyMedium)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={
                try{if(endpoint.isNotBlank())normalizeEndpoint(endpoint);onSave(current());result="";resultError=false}
                catch(e:Exception){result=e.message?:"请检查服务地址";resultError=true}
            }){Text("保存设置")}
            OutlinedButton(enabled=task?.isActive!=true,onClick={task=scope.launch{
                result="";resultError=false
                try{AiClient().test(current());result="文字连接成功。照片能力需通过手账中的整理功能验证。"}
                catch(e:CancellationException){throw e}catch(e:Exception){result=e.message?:"连接失败，请检查设置";resultError=true}finally{task=null}
            }}){Text("测试连接")}
        }}
        if(task?.isActive==true)item{AiProgress("正在测试文字连接…"){task?.cancel()}}
        if(result.isNotBlank())item{Message(result,resultError)}
        item{TextButton(onClick={confirmClear=true}){Text("清除已保存的 API 密钥")}}
        item{HorizontalDivider();Spacer(Modifier.height(18.dp));Text("手账与备份",style=MaterialTheme.typography.titleLarge)}
        item{Text("手账、照片、旅行计划和省份设计保存在这台设备上。卸载或更换手机前，请主动导出备份。未保存草稿和 AI 密钥不在备份中。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick={exporter.launch("旅页备份-${LocalDate.now()}.zip")},enabled=!archiveBusy){Icon(Icons.Outlined.FileDownload,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("导出备份")}
            OutlinedButton(onClick={importer.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!archiveBusy){Text("导入备份")}
        }}
        if(archiveBusy)item{LinearProgressIndicator(Modifier.fillMaxWidth());Message("正在处理备份，请稍候…")}
        item{HorizontalDivider();Spacer(Modifier.height(18.dp));Text("地图",style=MaterialTheme.typography.titleLarge)}
        item{Text("回家的地图中间有一个十字架：把省拖到十字架下再放大，就只会进这一个省。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("放大到最大直接进省",style=MaterialTheme.typography.bodyLarge)
                Text("北京、上海、天津、港澳这类小省，放大到底也占不满屏幕。开启后一碰顶就进省；关闭后它们不再“变大就进”，可在全国图上点开。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Spacer(Modifier.width(12.dp));Switch(checked=enterAtMaxZoom,onCheckedChange=onEnterAtMaxZoom)
        }}
        item{HorizontalDivider();Spacer(Modifier.height(18.dp));Text("当前主题 · 手绘日记",style=MaterialTheme.typography.titleLarge);Message("从一张省份地图出发，把回忆与下一程写进暖纸日记。");Text("旅页 ${versionName.ifBlank{"开发版"}} · 省份手账与旅行计划\nAI 提供建议，记录由你决定。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={showLicenses=true}){Text("图标开源许可")}}
    }
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("清除 AI 密钥？")},text={Text("本地手账和其他设置会保留。之后可重新填写密钥。")},confirmButton={TextButton(onClick={key="";onSave(current().copy(apiKey=""));confirmClear=false;result=""}){Text("清除")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("取消")}})
    if(importUri!=null)AlertDialog(onDismissRequest={importUri=null},title={Text("导入手账备份？")},text={Text("会合并手账、照片、想去清单和计划，同名记录作为新项导入。已有省份设计优先保留，尚未设计的省份使用备份内容。")},confirmButton={TextButton(onClick={
        val uri=importUri!!;importUri=null
        scope.launch{archiveBusy=true;try{val count=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{repo.importBackup(it)}?:error("无法读取备份文件")};onImported();onMessage("备份已合并：$count 篇手账，计划与省份设计也已处理")}
        catch(e:Exception){onMessage(e.message?:"导入失败，现有手账仍保留")}
        finally{archiveBusy=false}}
    }){Text("导入")}},dismissButton={TextButton(onClick={importUri=null}){Text("取消")}})
}
