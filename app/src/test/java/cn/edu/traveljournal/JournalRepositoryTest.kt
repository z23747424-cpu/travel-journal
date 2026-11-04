package cn.edu.traveljournal

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JournalRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: JournalRepository

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("travel-journal.db")
        File(context.filesDir, "photos").deleteRecursively()
        context.getSharedPreferences("travel-ai", Context.MODE_PRIVATE).edit().clear().commit()
        repository = JournalRepository(context)
    }
    @After fun close() { repository.close() }

    @Test fun journalsAndDraftSurviveReopenAndDeletionRemovesSearch() {
        val j = Journal(id = "j", title = "海边", body = "日落")
        repository.saveJournal(j)
        repository.saveDraft(j.copy(title = "未保存修改"))
        repository.close()
        repository = JournalRepository(context)
        assertEquals(j, repository.journals().single())
        assertEquals("未保存修改", repository.loadDraft()!!.title)
        repository.clearDraft()
        assertNull(repository.loadDraft())
        repository.deleteJournal(j.id)
        assertTrue(localSearch("海边", repository.journals()).isEmpty())
    }

    @Test fun wishDoesNotCreateACompletedTripAndAssociationSurvivesBackupConflict() {
        val destination = Destination(id = "qingdao", name = "青岛", region = "山东", sourceUrl = "https://example.org")
        repository.saveWish(Wish(id = "w", destination = destination))
        assertTrue(repository.journals().isEmpty())
        assertFalse(repository.wishes().single().visited)
        repository.saveJournal(Journal(id = "j", title = "青岛", body = "抵达", wishId = "w"))
        val out = ByteArrayOutputStream()
        repository.exportBackup(out)
        assertEquals(1, repository.importBackup(ByteArrayInputStream(out.toByteArray())))
        assertEquals(2, repository.journals().size)
        assertEquals(2, repository.wishes().size)
        repository.journals().forEach { j ->
            assertEquals(j.id, repository.wishes().single { it.id == j.wishId }.journalId)
        }
    }

    @Test fun photoBackupRoundTripPreservesPhotoOrderAndOmitsCredentials() {
        context.getSharedPreferences("travel-ai", Context.MODE_PRIVATE).edit()
            .putString("ciphertext", "sentinel-ciphertext-must-not-export")
            .putString("baseUrl", "https://private-provider.example/v1")
            .commit()
        val source = File(context.cacheDir, "source.png")
        source.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val photo = repository.importPhoto(Uri.fromFile(source))
        repository.saveJournal(Journal(id = "p", title = "照片", photos = listOf(photo)))
        val out = ByteArrayOutputStream()
        repository.exportBackup(out)
        var manifest = ""
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse(entry.name.contains("key", true))
                if (entry.name == "manifest.json") manifest = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertFalse(manifest.contains("apiKey"))
        assertFalse(manifest.contains("baseUrl"))
        assertFalse(manifest.contains("sentinel-ciphertext"))
        assertFalse(manifest.contains("private-provider"))
        repository.importBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, repository.journals().size)
        repository.journals().forEach { assertTrue(repository.photoFile(it.photos.single()).isFile) }
        assertNotEquals(repository.journals()[0].photos.single(), repository.journals()[1].photos.single())
    }

    @Test fun invalidArchiveCannotMutateExistingRecords() {
        repository.saveJournal(Journal(id = "existing", title = "原记录", body = "保留"))
        val malicious = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("../escape")); zip.write("bad".toByteArray()); zip.closeEntry()
        } }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { repository.importBackup(ByteArrayInputStream(malicious)) }
        assertEquals("existing", repository.journals().single().id)
        val invalidPhoto = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject().put("version", 1).put("journals", org.json.JSONArray()).put("wishes", org.json.JSONArray()).toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("photos/bad.jpg")); zip.write("not an image".toByteArray()); zip.closeEntry()
        } }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { repository.importBackup(ByteArrayInputStream(invalidPhoto)) }
        assertEquals(1, repository.journals().size)
    }

    @Test fun repositoryRejectsPathNamesAndMissingPhotos() {
        assertThrows(IllegalArgumentException::class.java) { repository.photoFile("../secret") }
        assertThrows(IllegalArgumentException::class.java) { repository.saveJournal(Journal(title = "照片缺失", photos = listOf("missing.jpg"))) }
    }

    @Test fun sharedPhotosRemainUntilTheLastRecordAndDraftReleaseThem() {
        val source = File(context.cacheDir, "shared.png")
        source.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val photo = repository.importPhoto(Uri.fromFile(source))
        repository.saveJournal(Journal(id = "a", title = "第一篇", photos = listOf(photo)))
        repository.saveJournal(Journal(id = "b", title = "第二篇", photos = listOf(photo)))
        repository.saveDraft(Journal(title = "草稿", photos = listOf(photo)))
        repository.deleteJournal("a")
        repository.deleteJournal("b")
        assertTrue(repository.photoFile(photo).exists())
        repository.clearDraft()
        assertFalse(repository.photoFile(photo).exists())
    }

    @Test fun clearingAnApiKeyDoesNotRequireKeystoreAndConfigMetadataPersists() {
        repository.saveConfig(AiConfig(baseUrl = "https://example.org/v1/", model = "test-model", spiritName = "小旅", consent = true))
        val saved = repository.loadConfig()
        assertEquals("https://example.org/v1/chat/completions", saved.baseUrl)
        assertEquals("", saved.apiKey)
        assertEquals("小旅", saved.spiritName)
        assertTrue(saved.consent)
    }

    @Test fun changingWishAssociationKeepsBothSidesConsistent() {
        repository.saveJournal(Journal(id = "a", title = "第一篇", body = "文字"))
        repository.saveJournal(Journal(id = "b", title = "第二篇", body = "文字"))
        val wish = Wish(id = "w", destination = Destination(id = "d", name = "青岛"), journalId = "a")
        repository.saveWish(wish)
        assertEquals("w", repository.journals().single { it.id == "a" }.wishId)
        repository.saveWish(wish.copy(journalId = "b"))
        assertNull(repository.journals().single { it.id == "a" }.wishId)
        assertEquals("w", repository.journals().single { it.id == "b" }.wishId)
        repository.saveWish(wish.copy(journalId = null))
        assertTrue(repository.journals().all { it.wishId == null })
    }

    @Test fun portraitExifOrientationRotatesPixelsBeforeTheAiCopyIsEncoded() {
        val source = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        val rotated = orientBitmap(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(4, rotated.width)
        assertEquals(8, rotated.height)
        assertEquals(8, source.width)
        rotated.recycle()
        source.recycle()
    }

    @Test fun unfinishedDateIsRetainedInDraftButRejectedAsAFinalRecord() {
        val unfinished = Journal(title = "还在填写", body = "正文", date = "2026-11-")
        repository.saveDraft(unfinished)
        assertEquals("2026-11-", repository.loadDraft()!!.date)
        assertThrows(IllegalArgumentException::class.java) { repository.saveJournal(unfinished) }
        assertTrue(repository.journals().isEmpty())
    }
}
