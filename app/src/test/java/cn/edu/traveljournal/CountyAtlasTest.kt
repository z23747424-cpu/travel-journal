package cn.edu.traveljournal

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 区县分片数据、区县层插旗规则、封面字段与「不再限制 12 张照片」的回归测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountyAtlasTest {
    private lateinit var context: Context
    private lateinit var repository: JournalRepository

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("travel-journal.db")
        File(context.filesDir, "photos").deleteRecursively()
        repository = JournalRepository(context)
    }

    @After fun close() { repository.close() }

    @Test fun offlineCountyIndexAndSlicesLoadFromAssets() {
        val index = CountyAtlas.index(context)
        // 台湾省的 TW… 代码不进选择表，其余 2850 个单位全部可落到计划上。
        assertEquals(2850, index.size)
        assertTrue(index.all { it.code.length == 6 && it.provinceCode.length == 6 && it.name.isNotBlank() })
        // 台湾省只有 TW… 代码，按“区县只收 6 位国标码”的规则它停在省级。
        assertEquals(33, index.map { it.provinceCode }.distinct().size)
        val beijing = index.filter { it.provinceCode == "110000" }
        assertEquals(16, beijing.size)
        assertTrue(beijing.any { it.name == "东城区" })
        val units = CountyAtlas.units(context, "110000")
        assertEquals(16, units.size)
        assertTrue(units.all { it.provinceCode == "110000" && it.provinceName == "北京市" })
        val dongcheng = units.single { it.code == "110101" }
        assertEquals("东城区", dongcheng.name)
        assertEquals(dongcheng.code, countyAt(dongcheng.center, units)?.code)
        assertNull(countyAt(MapPoint(39.9, 82.0), units))
        assertTrue(CountyAtlas.units(context, "").isEmpty())
    }

    @Test fun unsupportedCountyDataVersionIsRejected() {
        val failure = runCatching { CountyAtlas.parseIndex("""{"version":2,"units":[]}""") }
        assertTrue(failure.isFailure)
        val units = runCatching { CountyAtlas.parseUnits("""{"version":9,"provinceCode":"110000","provinceName":"北京市","units":[]}""") }
        assertTrue(units.isFailure)
    }

    @Test fun countyLayerOnlyFlagsPlansThatNameACounty() {
        val units = CountyAtlas.units(context, "330000")
        assertTrue(units.size > 10)
        val target = units.first { it.name == "西湖区" }
        val precise = TravelPlan(destination = target.name, provinceCode = "330000", countyCode = target.code, countyName = target.name)
        assertEquals(setOf(target.code), countyMapState(emptyList(), listOf(precise), units).marked)
        // 只填省的计划只参与省级标志，不在区县层乱插旗。
        val vague = TravelPlan(destination = "浙江自驾", provinceCode = "330000")
        assertTrue(countyMapState(emptyList(), listOf(vague), units).marked.isEmpty())
        // 手账靠坐标落到区县，并计入「去过」。
        val journal = Journal(provinceCode = "330000", latitude = target.center.latitude, longitude = target.center.longitude, visited = true)
        val state = countyMapState(listOf(journal), emptyList(), units)
        assertEquals(setOf(countyAt(target.center, units)?.code), state.visited)
        assertTrue(state.marked.isEmpty())
        // 没有区县几何数据时什么都不标，避免画出错误的格子。
        assertTrue(countyMapState(listOf(journal), listOf(precise), emptyList()).let { it.visited.isEmpty() && it.marked.isEmpty() })
    }

    @Test fun countyRefForTextPrefersTheNamedProvince() {
        val index = CountyAtlas.index(context)
        val same = countyRefForText(index, "330000", "去西湖区看日落")
        assertEquals("330000", same?.provinceCode)
        assertEquals("西湖区", same?.name)
        assertNull(countyRefForText(index, null, "随便走走"))
        assertEquals(same?.code, countyRefOf(index, same?.code)?.code)
        assertNull(countyRefOf(index, "000000"))
    }

    @Test fun legacyPlanJsonStillLoadsWithoutCountyAndRejectsBadCounty() {
        val legacy = planFromJson(JSONObject("""{"id":"p1","destination":"绍兴","provinceCode":"330000","status":"planned","createdAt":0,"updatedAt":0,"sources":[],"days":[{"id":"d1","activities":[]}]}"""))
        assertNull(legacy.countyCode)
        assertEquals("", legacy.countyName)
        assertEquals("planned", legacy.status)
        validateTravelPlan(legacy)
        // 区县必须有省、必须是 6 位代码、名字不能空。
        assertTrue(runCatching { validateTravelPlan(legacy.copy(countyCode = "330100", countyName = "杭州市", provinceCode = null)) }.isFailure)
        assertTrue(runCatching { validateTravelPlan(legacy.copy(countyCode = "33", countyName = "杭州市")) }.isFailure)
        assertTrue(runCatching { validateTravelPlan(legacy.copy(countyCode = "330106", countyName = "")) }.isFailure)
        validateTravelPlan(legacy.copy(countyCode = "330106", countyName = "西湖区"))
        assertEquals("330106", planFromJson(JSONObject(planJson(legacy.copy(countyCode = "330106", countyName = "西湖区")).toString())).countyCode)
    }

    @Test fun coverPhotoFallsBackAndSurvivesBackupJson() {
        val names = (1..13).map { "p$it.jpg" }
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        names.forEach { File(dir, it).writeBytes(byteArrayOf(1, 2, 3)) }
        // 12 张上限已解除。
        val journal = Journal(id = "j-cover", title = "十三张照片", photos = names, coverPhoto = "p7.jpg")
        repository.saveJournal(journal)
        assertEquals("p7.jpg", journalCover(repository.journals().single { it.id == "j-cover" }))
        assertEquals("p1.jpg", journalCover(journal.copy(coverPhoto = "gone.jpg")))
        assertEquals("p1.jpg", journalCover(journal.copy(coverPhoto = null)))
        assertNull(journalCover(journal.copy(photos = emptyList())))
        // 备份两条路径都要带上封面。
        val restored = journalFromJson(JSONObject(journalJson(journal).toString()))
        assertEquals("p7.jpg", restored.coverPhoto)
        val legacy = journalFromJson(JSONObject("""{"id":"old","title":"旧档","date":"2024-05-01","photos":["p1.jpg"],"createdAt":0,"updatedAt":0}"""))
        assertNull(legacy.coverPhoto)
        assertEquals("p1.jpg", journalCover(legacy))
        // 封面必须是自己的照片，重名照片仍然拒绝。
        assertTrue(runCatching { repository.saveJournal(journal.copy(id = "j-bad", coverPhoto = "gone.jpg")) }.isFailure)
        assertTrue(runCatching { repository.saveJournal(Journal(id = "j-dup", photos = listOf("p1.jpg", "p1.jpg"))) }.isFailure)
    }
}