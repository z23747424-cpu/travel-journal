package cn.edu.traveljournal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Android Compose interaction on Robolectric, explicitly not a connected-device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeUiFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun provinceNoteWithoutTitleAndManualPlanCanBeSavedFromRealUi(){
        ui.waitUntil(15000){ui.onAllNodesWithText("我的旅行地图").fetchSemanticsNodes().isNotEmpty()}
        capture("phone-map")
        ui.onNodeWithText("记一笔",substring=false).performClick()
        ui.onNodeWithText("选择省份",substring=false).performClick()
        ui.onNodeWithText("浙江省",substring=false).performScrollTo().performClick()
        capture("phone-after-selection")
        ui.onNodeWithTag("journal-editor").performScrollToNode(hasText("城市（选填）"))
        ui.onNodeWithText("城市（选填）").performTextInput("杭州")
        capture("phone-editor")
        ui.onNodeWithTag("journal-editor").performScrollToNode(hasText("这一路的故事"))
        ui.onNodeWithText("这一路的故事").performTextInput("今天在湖边慢慢走")
        ui.onNodeWithTag("journal-editor").performScrollToNode(hasText("保存",substring=false))
        ui.onNodeWithText("保存",substring=false).performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("今天在湖边慢慢走").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("返回").performClick()
        ui.onNodeWithText("灵感",substring=false).performClick()
        capture("phone-inspiration")
        ui.onNodeWithText("我的计划",substring=false).performClick()
        ui.onNodeWithText("自己写计划",substring=false).performClick()
        ui.onNodeWithText("目的地（必填）").performTextInput("青岛")
        capture("phone-plan-editor")
        ui.onNodeWithTag("plan-editor").performScrollToNode(hasText("添加一天 · 1/30",substring=false))
        ui.onNodeWithText("添加一天 · 1/30",substring=false).performClick()
        ui.onNodeWithTag("plan-editor").performScrollToNode(hasText("保存计划",substring=false))
        ui.onNodeWithText("保存计划",substring=false).performClick()
        ui.onNodeWithText("确认保存",substring=false).performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("青岛 · 2 天 · 日期待定",substring=false).fetchSemanticsNodes().isNotEmpty()}
        capture("phone-plans")
    }
    private fun capture(name:String){
        val cwd=File(System.getProperty("user.dir"));val app=if(File(cwd,"src/main").isDirectory)cwd else File(cwd,"app")
        val output=File(app,"build/reports/native-ui-15").apply{mkdirs()}
        // PixelCopy frame commits require a real display and time out in Robolectric.
        // Draw the native view tree to a software bitmap; never label this a device screenshot.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6));ui.waitForIdle()
        ui.runOnIdle{val view=ui.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1),view.height.coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));File(output,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
}
