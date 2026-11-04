package cn.edu.traveljournal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.BitmapFactory
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All methods are blocking: callers use Dispatchers.IO. No method performs a model request. */
class JournalRepository(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val helper = JournalDatabase(app)
    private val photoDir = File(app.filesDir, "photos").apply { check(mkdirs() || isDirectory) }
    private val preferences = app.getSharedPreferences("travel-ai", Context.MODE_PRIVATE)

    @Synchronized fun journals(): List<Journal> = readRows("journals", ::journalFromJson)
        .sortedWith(compareByDescending<Journal> { it.date }.thenByDescending { it.updatedAt })

    @Synchronized fun saveJournal(j: Journal) {
        validateJournal(j)
        require(j.wishId == null || wishes().any { it.id == j.wishId }) { "关联的收藏已不存在，请重新选择" }
        j.photos.forEach { require(photoFile(it).isFile) { "记录中有照片缺失，请重新选择照片" } }
        val previous = journals().find { it.id == j.id }
        transaction {
            if (j.wishId != null) journals().filter { it.id != j.id && it.wishId == j.wishId }
                .forEach { put("journals", it.id, journalJson(it.copy(wishId = null))) }
            put("journals", j.id, journalJson(j))
            wishes().forEach { wish ->
                when {
                    wish.id == j.wishId -> put("wishes", wish.id, wishJson(wish.copy(journalId = j.id)))
                    wish.journalId == j.id -> put("wishes", wish.id, wishJson(wish.copy(journalId = null)))
                }
            }
        }
        cleanupPhotos(previous?.photos.orEmpty() - j.photos.toSet())
    }

    @Synchronized fun deleteJournal(id: String) {
        val old = journals().find { it.id == id } ?: return
        transaction {
            helper.writableDatabase.delete("journals", "id=?", arrayOf(id))
            wishes().filter { it.journalId == id }.forEach { put("wishes", it.id, wishJson(it.copy(journalId = null))) }
        }
        cleanupPhotos(old.photos)
    }

    @Synchronized fun wishes(): List<Wish> = readRows("wishes", ::wishFromJson).sortedByDescending { it.createdAt }

    @Synchronized fun saveWish(w: Wish) {
        validateWish(w)
        val entries = journals()
        require(w.journalId == null || entries.any { it.id == w.journalId }) { "关联的旅行记录不存在" }
        transaction {
            entries.forEach { entry ->
                when {
                    entry.id == w.journalId -> put("journals", entry.id, journalJson(entry.copy(wishId = w.id)))
                    entry.wishId == w.id -> put("journals", entry.id, journalJson(entry.copy(wishId = null)))
                }
            }
            if (w.journalId != null) wishes().filter { it.id != w.id && it.journalId == w.journalId }
                .forEach { put("wishes", it.id, wishJson(it.copy(journalId = null))) }
            put("wishes", w.id, wishJson(w))
        }
    }

    @Synchronized fun deleteWish(id: String) {
        transaction {
            helper.writableDatabase.delete("wishes", "id=?", arrayOf(id))
            journals().filter { it.wishId == id }.forEach { put("journals", it.id, journalJson(it.copy(wishId = null))) }
            loadDraft()?.takeIf { it.wishId == id }?.let { put("draft", "current", journalJson(it.copy(wishId = null))) }
        }
    }

    fun destinations(): List<Destination> = app.assets.open("destinations.json").bufferedReader().use { reader ->
        val text = reader.readText()
        val array = if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text).getJSONArray("destinations")
        require(array.length() <= 1000) { "目的地资料过大" }
        (0 until array.length()).map { destinationFromJson(array.getJSONObject(it)).also(::validateDestination) }
    }

    fun photoFile(name: String): File {
        require(PHOTO_NAME.matches(name)) { "照片文件名无效" }
        return File(photoDir, name).also { require(it.canonicalFile.parentFile == photoDir.canonicalFile) { "照片路径无效" } }
    }

    @Synchronized fun importPhoto(uri: Uri): String {
        val temp = File(photoDir, "${UUID.randomUUID()}.tmp")
        try {
            val source = app.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("无法读取选中的照片")
            source.use { input -> temp.outputStream().use { copyLimited(input, it, MAX_PHOTO_BYTES) } }
            val mime = validatePhoto(temp)
            val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"; else -> "jpg" }
            val name = unusedPhotoName(extension)
            java.nio.file.Files.move(temp.toPath(), photoFile(name).toPath())
            return name
        } catch (_: SecurityException) {
            throw IllegalArgumentException("无法读取这张照片，请在系统选择器中重新选择")
        } catch (_: java.io.IOException) {
            throw IllegalArgumentException("照片读取或复制失败，请检查文件和剩余存储空间")
        } finally { temp.delete() }
    }

    @Synchronized fun saveDraft(j: Journal) {
        validateJournal(j, draft = true)
        j.photos.forEach { require(photoFile(it).isFile) { "草稿中有照片缺失" } }
        val previous = loadDraft()
        transaction { put("draft", "current", journalJson(j)) }
        cleanupPhotos(previous?.photos.orEmpty() - j.photos.toSet())
    }

    @Synchronized fun loadDraft(): Journal? = readRows("draft", ::journalFromJson).firstOrNull()

    @Synchronized fun clearDraft() {
        val previous = loadDraft()
        helper.writableDatabase.delete("draft", "id=?", arrayOf("current"))
        cleanupPhotos(previous?.photos.orEmpty())
    }

    @Synchronized fun loadConfig(): AiConfig {
        val cipherText = preferences.getString("ciphertext", "").orEmpty()
        val apiKey = if (cipherText.isBlank()) "" else try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(preferences.getString("iv", ""), Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("设备无法解密已保存的密钥，请清除密钥并重新填写") }
        return AiConfig(preferences.getString("baseUrl", "").orEmpty(), apiKey,
            preferences.getString("model", "").orEmpty(), preferences.getString("spiritName", "").orEmpty(), preferences.getBoolean("consent", false))
    }

    @Synchronized fun saveConfig(c: AiConfig) {
        require(c.model.length <= 200 && c.spiritName.length <= 80 && c.apiKey.length <= 8192) { "AI 配置过长" }
        require(!c.apiKey.contains('\n') && !c.apiKey.contains('\r')) { "密钥不能包含换行" }
        val endpoint = if (c.baseUrl.isBlank()) "" else normalizeEndpoint(c.baseUrl)
        val editor = preferences.edit().putString("baseUrl", endpoint).putString("model", c.model.trim())
            .putString("spiritName", c.spiritName.trim()).putBoolean("consent", c.consent)
        if (c.apiKey.isBlank()) editor.remove("iv").remove("ciphertext") else {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
                val encrypted = cipher.doFinal(c.apiKey.trim().toByteArray(Charsets.UTF_8))
                editor.putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            } catch (_: Exception) { throw IllegalStateException("设备安全存储不可用，密钥未保存") }
        }
        check(editor.commit()) { "配置保存失败，请检查存储空间" }
    }

    @Synchronized fun exportBackup(out: OutputStream) {
        try { writeBackup(out, journals(), wishes(), ::photoFile) }
        catch (_: java.io.IOException) { throw IllegalStateException("备份写入失败，请检查目标文件位置和剩余空间") }
    }

    @Synchronized fun importBackup(input: InputStream): Int = readBackup(input, app.cacheDir) { entries, wishes, photos ->
        val journalIds = journals().map { it.id }.toMutableSet()
        val wishIds = this.wishes().map { it.id }.toMutableSet()
        val journalMap = entries.associate { it.id to newIdIfNeeded(it.id, journalIds) }
        val wishMap = wishes.associate { it.id to newIdIfNeeded(it.id, wishIds) }
        val reserved = mutableSetOf<String>()
        val photoMap = photos.keys.associateWith { unusedPhotoName(it.substringAfterLast('.'), reserved).also { name -> reserved.add(name) } }
        val copied = mutableListOf<File>()
        try {
            photos.forEach { (name, source) ->
                val target = photoFile(photoMap.getValue(name))
                check(target.createNewFile()) { "导入照片文件名冲突，请重试" }
                copied += target
                source.inputStream().use { from -> target.outputStream().use { to -> copyLimited(from, to, MAX_PHOTO_BYTES) } }
            }
            transaction {
                entries.forEach { journal ->
                    val imported = journal.copy(id = journalMap.getValue(journal.id), photos = journal.photos.map(photoMap::getValue), wishId = journal.wishId?.let(wishMap::getValue))
                    put("journals", imported.id, journalJson(imported))
                }
                wishes.forEach { wish ->
                    val imported = wish.copy(id = wishMap.getValue(wish.id), journalId = wish.journalId?.let(journalMap::getValue))
                    put("wishes", imported.id, wishJson(imported))
                }
            }
            entries.size
        } catch (failure: Throwable) { copied.forEach { it.delete() }; throw failure }
    }

    @Synchronized override fun close() = helper.close()

    private fun <T> readRows(table: String, parse: (JSONObject) -> T): List<T> = helper.readableDatabase
        .query(table, arrayOf("payload"), null, null, null, null, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(parse(JSONObject(cursor.getString(0)))) }
        }

    private fun put(table: String, id: String, json: JSONObject) {
        val values = ContentValues().apply { put("id", id); put("payload", json.toString()) }
        check(helper.writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "记录保存失败" }
    }

    private fun transaction(block: () -> Unit) {
        val database = helper.writableDatabase
        database.beginTransaction()
        try { block(); database.setTransactionSuccessful() } finally { database.endTransaction() }
    }

    private fun cleanupPhotos(candidates: Collection<String>) {
        if (candidates.isEmpty()) return
        val referenced = journals().flatMap { it.photos }.toSet() + loadDraft()?.photos.orEmpty()
        candidates.filterNot(referenced::contains).forEach { photoFile(it).delete() }
    }

    private fun unusedPhotoName(extension: String, reserved: Set<String> = emptySet()): String {
        var name: String
        do { name = "${UUID.randomUUID()}.$extension" } while (name in reserved || photoFile(name).exists())
        return name
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("travel-journal-api-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("travel-journal-api-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}

private class JournalDatabase(context: Context) : SQLiteOpenHelper(context, "travel-journal.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        listOf("journals", "wishes", "draft").forEach { db.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)") }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("不支持的数据版本，请保留备份") }
}

internal val PHOTO_NAME = Regex("[A-Za-z0-9_-]{1,100}\\.(jpg|jpeg|png|webp|heic|heif)")
internal const val MAX_PHOTO_BYTES = 25L * 1024 * 1024

internal fun validatePhoto(file: File): String {
    require(file.length() in 1..MAX_PHOTO_BYTES) { "照片大小无效，单张最多 25 MB" }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 60_000_000) { "照片内容无法识别或分辨率过大" }
    require(options.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "不支持的照片格式" }
    var sample = 1
    while (options.outWidth / sample > 1024 || options.outHeight / sample > 1024) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("照片数据损坏，无法解码")
    bitmap.recycle()
    return options.outMimeType
}

internal fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count == -1) return total
        total += count
        require(total <= maxBytes) { "文件超过允许的大小" }
        output.write(buffer, 0, count)
    }
}

private fun newIdIfNeeded(id: String, used: MutableSet<String>): String {
    var candidate = id
    while (!used.add(candidate)) candidate = UUID.randomUUID().toString()
    return candidate
}

internal fun validateJournal(j: Journal, draft: Boolean = false) {
    require(j.id.isNotBlank() && j.id.length <= 128) { "记录 ID 无效" }
    require(draft || j.title.isNotBlank()) { "请填写标题" }
    require(draft || j.body.isNotBlank() || j.photos.isNotEmpty()) { "请至少填写正文或选择照片" }
    require(j.title.length <= 200 && j.place.length <= 300 && j.body.length <= 100_000 && j.photoDescription.length <= 5000 && j.date.length <= 20) { "记录内容过长" }
    require(j.photos.size <= 12 && j.photos.distinct().size == j.photos.size && j.photos.all(PHOTO_NAME::matches)) { "每篇最多 12 张不同照片" }
    require(j.tags.size <= 24 && j.tags.all { it.length <= 80 }) { "标签过多或过长" }
    require(j.createdAt >= 0 && j.updatedAt >= 0) { "记录时间无效" }
    require(j.wishId == null || j.wishId.isNotBlank() && j.wishId.length <= 128) { "收藏关联无效" }
    if (!draft) try { LocalDate.parse(j.date) } catch (_: Exception) { throw IllegalArgumentException("日期请使用 yyyy-MM-dd 格式") }
}

internal fun validateWish(w: Wish) {
    require(w.id.isNotBlank() && w.id.length <= 128 && w.createdAt >= 0) { "收藏数据无效" }
    require(w.query.length <= 5000 && w.reason.length <= 5000 && w.notes.length <= 20_000) { "收藏内容过长" }
    require(w.journalId == null || w.journalId.isNotBlank() && w.journalId.length <= 128) { "旅行关联无效" }
    validateDestination(w.destination)
}

internal fun validateDestination(d: Destination) {
    require(d.id.isNotBlank() && d.id.length <= 128 && d.name.isNotBlank() && d.name.length <= 200) { "目的地数据无效" }
    require(d.region.length <= 300 && d.summary.length <= 10_000 && d.days.length <= 300 && d.season.length <= 1000 && d.sourceUrl.length <= 2000 && d.checkedAt.length <= 100) { "目的地资料过长" }
    require(d.tags.size <= 50 && d.tags.all { it.length <= 100 }) { "目的地标签无效" }
    if (d.sourceUrl.isNotBlank()) {
        val source = try { java.net.URI(d.sourceUrl) } catch (_: Exception) { throw IllegalArgumentException("目的地来源地址无效") }
        require(source.scheme in setOf("https", "http") && !source.host.isNullOrBlank() && source.userInfo == null) { "目的地来源必须是有效的网页地址" }
    }
}
