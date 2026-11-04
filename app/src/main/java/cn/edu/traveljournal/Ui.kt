package cn.edu.traveljournal

import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val Paper = Color(0xFFF7F5EF)
private val Ink = Color(0xFF252E2A)
private val Green = Color(0xFF385B50)

@Composable fun TravelTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(primary = Green, onPrimary = Color.White,
        secondary = Color(0xFF825A42), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8ECE3), onSecondaryContainer = Ink,
        background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
        surfaceVariant = Color(0xFFEDEFE7), onSurfaceVariant = Color(0xFF59655E),
        outline = Color(0xFF747F76), outlineVariant = Color(0xFFD8DCD3),
        error = Color(0xFFAA3432))
    val typography = Typography(
        headlineLarge = Typography().headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
        headlineMedium = Typography().headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
        titleLarge = Typography().titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
        bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = Typography().bodyMedium.copy(lineHeight = 23.sp))
    MaterialTheme(colorScheme = colors, typography = typography,
        shapes = Shapes(small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(16.dp)), content = content)
}

@Composable fun PageTitle(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TagRow(tags: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.take(12).forEach { tag ->
            Text(tag, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 9.dp, vertical = 5.dp))
        }
    }
}

@Composable fun EmptyLandscape(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        drawRect(Color(0xFFE7ECE4))
        drawCircle(Color(0xFFD5AB7F), size.width * .07f, androidx.compose.ui.geometry.Offset(size.width * .76f, size.height * .28f))
        val far = Path().apply {
            moveTo(0f, size.height * .75f); cubicTo(size.width * .2f,size.height*.1f,size.width*.35f,size.height*.4f,size.width*.65f,size.height*.75f)
            lineTo(size.width,size.height*.75f); lineTo(size.width,size.height);lineTo(0f,size.height);close()
        }
        drawPath(far, Color(0xFFBAC9B6))
        val near = Path().apply { moveTo(0f,size.height*.8f); cubicTo(size.width*.5f,size.height*.45f,size.width*.5f,size.height*.7f,size.width,size.height*.57f);lineTo(size.width,size.height);lineTo(0f,size.height);close() }
        drawPath(near,line.copy(alpha=.65f))
    }
}

@Composable fun LocalPhoto(file: File?, description: String, modifier: Modifier = Modifier, fit: Boolean = false) {
    var loaded by remember(file?.absolutePath) { mutableStateOf(false) }
    var bitmap by remember(file?.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file?.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            if (file == null || !file.isFile) null else runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1400).coerceAtLeast(1) }
                val raw = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
                val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1)
                val matrix = Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                        7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
                    }
                }
                if (orientation == 1) raw else android.graphics.Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true)
            }.getOrNull()
        }
        loaded = true
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = description,
        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Text(if (file == null) "一段旅程，一页回忆" else if(!loaded) "照片载入中…" else "照片暂时无法显示", modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun AiProgress(label: String, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable fun Message(text: String, error: Boolean = false) {
    if (text.isNotBlank()) Text(text, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
}
