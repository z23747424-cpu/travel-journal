package cn.edu.traveljournal

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.json.JSONObject
import org.json.JSONArray

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeFeaturePersistenceTest {
    private lateinit var context:Context
    private lateinit var repo:JournalRepository
    @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("travel-journal.db");repo=JournalRepository(context)}
    @After fun close(){repo.close()}
    private fun photo():String{val file=File(context.cacheDir,"native-marker.png");file.outputStream().use{Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).apply{eraseColor(0x8800AA66.toInt())}.compress(Bitmap.CompressFormat.PNG,100,it)};return repo.importPhoto(Uri.fromFile(file))}
    private fun manifest(bytes:ByteArray):JSONObject=ZipInputStream(ByteArrayInputStream(bytes)).use{zip->while(true){val entry=zip.nextEntry?:error("missing manifest");if(entry.name=="manifest.json")return@use JSONObject(zip.readBytes().toString(Charsets.UTF_8))};error("unreachable")}

    @Test fun optionalTitleAndCitySaveAndRemainIndependentOfProvinceDesign(){
        val photo=photo();val original=repo.photoFile(photo).readBytes()
        repo.saveJournal(Journal(id="j",body="今天的风",photos=listOf(photo),provinceCode="330000"))
        val design=ProvinceDesign("330000",color="#CFAF9F",mode="photos",photos=listOf(photo),marker=WishMarker("image","#AC7850",32,photo))
        repo.saveProvinceDesign(design)
        repo.saveJournal(Journal(id="j2",body="另一页，不重置装饰",provinceCode="330000"))
        repo.deleteJournal("j")
        assertEquals(design,repo.provinceDesigns().single())
        assertArrayEquals(original,repo.photoFile(photo).readBytes())
        assertEquals("另一页，不重置装饰",journalDisplayTitle(repo.journals().single()))
    }
    @Test fun planAndDraftSurviveReopenAndAtomicSaveClearsOnlyMatchingDraft(){
        val p=TravelPlan(id="p",destination="京都",days=listOf(PlanDay(title="散步",activities=listOf(PlanActivity(place="街巷",description="慢慢走")))))
        repo.savePlan(p);repo.savePlanDraft(p.copy(notes="未保存的想法",startDate="2026-"))
        repo.close();repo=JournalRepository(context)
        assertEquals(p,repo.plans().single());assertEquals("2026-",repo.loadPlanDraft()!!.startDate)
        assertThrows(IllegalArgumentException::class.java){repo.savePlan(repo.loadPlanDraft()!!)}
        val other=TravelPlan(id="other",destination="海边")
        repo.savePlan(other);assertNotNull(repo.loadPlanDraft())
        repo.savePlan(p.copy(notes="已保存"));assertNull(repo.loadPlanDraft())
        repo.saveJournal(Journal(id="record",body="回忆",provinceCode="370000"));repo.deletePlan("p")
        assertEquals("other",repo.plans().single().id);assertEquals("record",repo.journals().single().id)
    }
    @Test fun zipV2RoundTripKeepsPlanCustomImageAndExcludesCredentials(){
        val photo=photo();val bytes=repo.photoFile(photo).readBytes()
        val p=TravelPlan(id="trip",destination="青岛",provinceCode="370000",days=listOf(PlanDay(title="海风")))
        repo.savePlan(p);repo.saveProvinceDesign(ProvinceDesign("370000",photos=listOf(photo),marker=WishMarker("image",size=28,image=photo)))
        val out=ByteArrayOutputStream();repo.exportBackup(out);val json=manifest(out.toByteArray())
        assertEquals(2,json.getInt("version"));assertEquals(1,json.getJSONArray("plans").length());assertFalse(json.has("config"));assertFalse(json.toString().contains("apiKey"))
        repo.close();context.deleteDatabase("travel-journal.db");repo=JournalRepository(context);repo.importBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(p,repo.plans().single());val restored=repo.provinceDesigns().single();assertNotEquals(photo,restored.marker.image)
        assertArrayEquals(bytes,repo.photoFile(restored.marker.image!!).readBytes())
        assertEquals(restored.marker.image,restored.photos.single())
        // A second import never replaces the user's existing province design or plan.
        repo.saveProvinceDesign(restored.copy(color="#D5C5A4"));repo.importBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals("#D5C5A4",repo.provinceDesigns().single().color);assertEquals(2,repo.plans().size)
    }
    @Test fun legacyZipV1StillImports(){
        val out=ByteArrayOutputStream();ZipOutputStream(out).use{zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(JSONObject().put("version",1).put("journals",JSONArray().put(journalJson(Journal(id="legacy",title="旧笔记",body="原文")))).put("wishes",JSONArray()).toString().toByteArray());zip.closeEntry()}
        assertEquals(1,repo.importBackup(ByteArrayInputStream(out.toByteArray())));assertEquals("原文",repo.journals().single().body)
    }
    @Test fun invalidPlanBackupNeverPartiallyImports(){
        repo.saveJournal(Journal(id="existing",body="保留",provinceCode="330000"))
        val invalid=planJson(TravelPlan(destination="x")).put("provinceCode","unknown")
        val json=JSONObject().put("version",2).put("journals",JSONArray().put(journalJson(Journal(id="should-not-import",title="新",body="新")))).put("wishes",JSONArray()).put("plans",JSONArray().put(invalid)).put("provinceDesigns",JSONArray())
        val out=ByteArrayOutputStream();ZipOutputStream(out).use{zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(json.toString().toByteArray());zip.closeEntry()}
        assertThrows(IllegalArgumentException::class.java){repo.importBackup(ByteArrayInputStream(out.toByteArray()))}
        assertEquals(listOf("existing"),repo.journals().map{it.id});assertTrue(repo.plans().isEmpty())
    }
    @Test fun unusedImportedCopiesAreRemovedWithoutTouchingSavedOrDraftFiles(){
        val used=photo();val draft=photo();val design=photo();val abandoned=photo()
        repo.saveJournal(Journal(id="j",body="原文",photos=listOf(used)))
        repo.saveDraft(Journal(id="draft",photos=listOf(draft)))
        repo.saveProvinceDesign(ProvinceDesign("330000",marker=WishMarker("image",image=design)))
        repo.discardUnusedPhotos(listOf(used,draft,design,abandoned))
        assertTrue(repo.photoFile(used).isFile);assertTrue(repo.photoFile(draft).isFile);assertTrue(repo.photoFile(design).isFile);assertFalse(repo.photoFile(abandoned).exists())
        val orphan=photo();val restored=photo();repo.purgeUnusedPhotos(listOf(restored));assertFalse(repo.photoFile(orphan).exists());assertTrue(repo.photoFile(design).isFile);assertTrue(repo.photoFile(restored).isFile)
        assertThrows(IllegalArgumentException::class.java){repo.discardUnusedPhotos(listOf("../external.jpg"))}
    }
}
