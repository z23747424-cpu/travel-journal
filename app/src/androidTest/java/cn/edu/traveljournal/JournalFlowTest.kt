package cn.edu.traveljournal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises native Compose controls against real local storage; does not call an AI provider. */
class JournalFlowTest {
    @get:Rule val ui=createEmptyComposeRule()
    private fun shot(name:String){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        val directory=File(context.filesDir,"qa-screenshots").apply{mkdirs()}
        File(directory,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun journalPersistenceSearchAndWishAreSeparate(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=JournalRepository(context)
        val title="测试旅程-${System.currentTimeMillis()}"
        var savedId:String?=null
        val wish=Wish(destination=Destination(id="qa-destination",name="测试目的地",region="教学区域",sourceUrl="https://example.org"),query="想安静看海",reason="本地流程测试")
        repo.saveWish(wish)
        var scenario:ActivityScenario<MainActivity>?=null
        try{
            scenario=ActivityScenario.launch(MainActivity::class.java)
            ui.waitUntil(15000){ui.onAllNodesWithText("我的旅行地图").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithContentDescription("全部手账").performClick()
            ui.onNodeWithText("新建手账").performClick()
            ui.onNodeWithText("手账标题").performTextInput(title)
            ui.onNodeWithText("地点（可选）").performTextInput("海边")
            ui.onNodeWithText("这一路的故事").performScrollTo().performTextInput("在海边看日落，记住温暖的晚霞。")
            ui.onNodeWithText("保存").performScrollTo().performClick()
            ui.waitUntil(10000){repo.journals().any{it.title==title}}
            savedId=repo.journals().first{it.title==title}.id
            ui.waitUntil(10000){ui.onAllNodesWithText("编辑",substring=false).fetchSemanticsNodes().isNotEmpty()}
            shot("detail")
            scenario.close();scenario=ActivityScenario.launch(MainActivity::class.java)
            ui.waitUntil(15000){ui.onAllNodesWithText("我的旅行地图").fetchSemanticsNodes().isNotEmpty()}
            assertTrue(repo.journals().any{it.id==savedId})
            shot("home")
            ui.onNodeWithText("搜索").performClick()
            ui.onNodeWithText("想找哪一段回忆？").performTextInput("海边")
            ui.onNodeWithText("本地搜索").performClick()
            ui.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            shot("search")
            ui.onNodeWithText("灵感").performClick()
            shot("inspiration")
            ui.onNodeWithText("想去清单 · ${repo.wishes().size}").performClick()
            ui.onNodeWithText("测试目的地").assertIsDisplayed()
            ui.onNodeWithText("开始记录这次旅行").performClick()
            ui.onNodeWithText("手账标题").assertTextContains("测试目的地旅行")
            assertTrue(repo.journals().none{it.wishId==wish.id})
            ui.onNodeWithContentDescription("返回").performClick()
            ui.onNodeWithText("放弃",substring=false).performClick()
            ui.onNodeWithText("设置").performClick()
            shot("settings")
        }finally{
            scenario?.close();savedId?.let{repo.deleteJournal(it)};repo.deleteWish(wish.id);repo.clearDraft();repo.close()
        }
    }
}
