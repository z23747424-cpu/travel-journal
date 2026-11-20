package cn.edu.traveljournal

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NativeMigrationContractTest {
    @Test fun explicitProvinceAndOptionalCitySurviveRecordRoundTrip() {
        val json=journalJson(Journal(id="province-record",title="",body="湖边的风"))
            .put("provinceCode","330000").put("city","杭州")
        val restored=journalJson(journalFromJson(json))
        assertEquals("330000",restored.optString("provinceCode"))
        assertEquals("杭州",restored.optString("city"))
    }
    @Test fun existingDatabaseUpgradesWithoutDeletingJournal() {
        val context=RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("travel-journal.db")
        val old=context.openOrCreateDatabase("travel-journal.db",Context.MODE_PRIVATE,null)
        listOf("journals","wishes","draft").forEach{old.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL)")}
        val payload=journalJson(Journal(id="old",title="旧笔记",body="保留原文"))
        old.execSQL("INSERT INTO journals VALUES (?,?)",arrayOf("old",payload.toString()));old.version=1;old.close()
        JournalRepository(context).use{repo->assertEquals("保留原文",repo.journals().single().body)}
        context.openOrCreateDatabase("travel-journal.db",Context.MODE_PRIVATE,null).use{assertEquals(2,it.version)}
    }
}
