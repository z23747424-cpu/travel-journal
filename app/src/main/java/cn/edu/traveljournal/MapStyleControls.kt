package cn.edu.traveljournal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val cityPalette = listOf("#A9BFAE" to "鼠尾草绿", "#C4A58D" to "陶土色", "#DACAA4" to "沙色", "#A8BDC7" to "雾蓝色", "#CAB4BF" to "玫瑰色")
private val cityHex = Regex("^#[0-9A-Fa-f]{6}$")

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MapStyleControls(color:String, mode:String, hasPhotos:Boolean, onColor:(String)->Unit, onMode:(String)->Unit) {
    var custom by remember(color) { mutableStateOf(color) }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("城市图块样式",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            cityPalette.forEach { (hex,name) ->
                val selected=color.equals(hex,ignoreCase=true)
                IconButton(onClick={onColor(hex)},modifier=Modifier.size(48.dp).semantics { contentDescription="$name，${if(selected) "已选择" else "未选择"}" }) {
                    Box(Modifier.size(28.dp).background(Color(android.graphics.Color.parseColor(hex)),CircleShape).border(if(selected) 3.dp else 1.dp,MaterialTheme.colorScheme.onSurface,CircleShape))
                }
            }
        }
        OutlinedTextField(custom,{ value -> custom=value.take(7); if(cityHex.matches(custom)) onColor(custom.uppercase()) },label={Text("自定义颜色 #RRGGBB")},singleLine=true,isError=custom.isNotEmpty()&&!cityHex.matches(custom),supportingText={if(custom.isNotEmpty()&&!cityHex.matches(custom))Text("请输入完整的六位十六进制颜色")},modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=mode!="photos",onClick={onMode("color")},label={Text("纯色")})
            FilterChip(selected=mode=="photos",enabled=hasPhotos,onClick={onMode("photos")},label={Text("照片拼贴")})
        }
        if(!hasPhotos)Text("添加照片后可使用照片拼贴",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
