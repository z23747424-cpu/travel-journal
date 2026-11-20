package cn.edu.traveljournal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h873dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeUiViewportTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @After fun resetFont(){RuntimeEnvironment.setFontScale(1f)}
    @Test fun largerSystemFontKeepsMapAndPrimaryActionAccessible(){
        RuntimeEnvironment.setFontScale(1.3f);ui.activityRule.scenario.recreate()
        waitForMap();ui.onNodeWithText("记一笔",substring=false).assertIsDisplayed();ui.onNodeWithText("地图",substring=false).assertIsDisplayed();capture("phone-font130")
    }
    @Test @Config(qualifiers="w900dp-h1200dp-xhdpi") fun expandedScreenHasReachableNavigationAndMapAction(){
        waitForMap();ui.onNodeWithText("记一笔",substring=false).assertIsDisplayed();ui.onNodeWithText("灵感",substring=false).assertIsDisplayed();capture("tablet-map")
    }
    private fun waitForMap(){ui.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));ui.onAllNodesWithText("我的旅行地图").fetchSemanticsNodes().isNotEmpty()}}
    private fun capture(name:String){
        val cwd=File(System.getProperty("user.dir")!!);val app=if(File(cwd,"src/main").isDirectory)cwd else File(cwd,"app")
        val output=File(app,"build/reports/native-ui-15").apply{mkdirs()}
        ui.runOnIdle{val view=ui.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1),view.height.coerceAtLeast(1),android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));File(output,"$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
}
