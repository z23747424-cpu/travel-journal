package cn.edu.traveljournal

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MAX_ARCHIVE_BYTES = 250L * 1024 * 1024
private const val MAX_MANIFEST_BYTES = 12L * 1024 * 1024
private const val MAX_RECORDS = 2000
private const val MAX_FILES = 10000

internal fun validateBackupPath(name: String) {
    require(name == "manifest.json" || name.startsWith("photos/") && PHOTO_NAME.matches(name.removePrefix("photos/"))) { "备份包含不安全或未知的文件路径" }
}

internal fun writeBackup(out: OutputStream, journals: List<Journal>, wishes: List<Wish>, photoFile: (String) -> File) {
    require(journals.size <= MAX_RECORDS && wishes.size <= MAX_RECORDS) { "备份最多支持 2000 篇手账和 2000 项收藏" }
    val manifest = JSONObject().put("version", 1)
        .put("journals", JSONArray(journals.map(::journalJson)))
        .put("wishes", JSONArray(wishes.map(::wishJson)))
        .toString().toByteArray(Charsets.UTF_8)
    require(manifest.size <= MAX_MANIFEST_BYTES) { "文字内容过大，无法生成当前版本备份" }
    val names = journals.flatMap { it.photos }.distinct()
    require(names.size + 1 <= MAX_FILES) { "照片数量过多" }
    var total = manifest.size.toLong()
    names.forEach { name ->
        val file = photoFile(name)
        validatePhoto(file)
        total += file.length()
        require(total <= MAX_ARCHIVE_BYTES) { "备份超过 250 MB，请减少照片后重试" }
    }
    // This explicit allowlist deliberately excludes preferences, credentials and device keys.
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifest)
        zip.closeEntry()
        names.forEach { name ->
            zip.putNextEntry(ZipEntry("photos/$name"))
            photoFile(name).inputStream().use { copyLimited(it, zip, MAX_PHOTO_BYTES) }
            zip.closeEntry()
        }
    }
}

internal fun readBackup(input: InputStream, cacheDir: File, merge: (List<Journal>, List<Wish>, Map<String, File>) -> Int): Int {
    val staging = File(cacheDir, "backup-import-${UUID.randomUUID()}")
    check(staging.mkdirs()) { "无法建立备份临时目录" }
    try {
        val seen = mutableSetOf<String>()
        val photos = linkedMapOf<String, File>()
        var manifest: ByteArray? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateBackupPath(entry.name)
                require(!entry.isDirectory && seen.add(entry.name) && seen.size <= MAX_FILES) { "备份文件重复或数量过多" }
                val entryLimit = if (entry.name == "manifest.json") MAX_MANIFEST_BYTES else MAX_PHOTO_BYTES
                require(entry.size <= entryLimit) { "备份中的文件过大" }
                val available = minOf(entryLimit, MAX_ARCHIVE_BYTES - total)
                require(available > 0) { "备份超过 250 MB" }
                if (entry.name == "manifest.json") {
                    val bytes = ByteArrayOutputStream()
                    total += copyLimited(zip, bytes, available)
                    manifest = bytes.toByteArray()
                } else {
                    val name = entry.name.removePrefix("photos/")
                    val file = File(staging, name)
                    total += file.outputStream().use { copyLimited(zip, it, available) }
                    validatePhoto(file)
                    photos[name] = file
                }
                zip.closeEntry()
            }
        }
        val json = JSONObject(String(requireNotNull(manifest) { "备份缺少 manifest.json" }, Charsets.UTF_8))
        require(json.get("version") is Number && json.getInt("version") == 1 && json.getDouble("version") == 1.0) { "不支持的备份版本" }
        val journalArray = json.getJSONArray("journals")
        val wishArray = json.getJSONArray("wishes")
        require(journalArray.length() <= MAX_RECORDS && wishArray.length() <= MAX_RECORDS) { "备份记录数量过多" }
        val journals = (0 until journalArray.length()).map { journalFromJson(journalArray.getJSONObject(it)).also { journal -> validateJournal(journal) } }
        val wishes = (0 until wishArray.length()).map { wishFromJson(wishArray.getJSONObject(it)).also(::validateWish) }
        val journalIds = journals.map { it.id }.toSet()
        val wishIds = wishes.map { it.id }.toSet()
        require(journalIds.size == journals.size && wishIds.size == wishes.size) { "备份包含重复记录 ID" }
        require(journals.all { it.wishId == null || it.wishId in wishIds } && wishes.all { it.journalId == null || it.journalId in journalIds }) { "备份中的旅行与收藏关联不完整" }
        require(journals.all { journal -> journal.wishId == null || wishes.single { it.id == journal.wishId }.journalId == journal.id } &&
            wishes.all { wish -> wish.journalId == null || journals.single { it.id == wish.journalId }.wishId == wish.id }) { "备份中的旅行与收藏关联不一致" }
        require(journals.flatMap { it.photos }.toSet() == photos.keys) { "备份中存在缺失或未引用的照片" }
        return merge(journals, wishes, photos)
    } catch (e: org.json.JSONException) {
        throw IllegalArgumentException("备份中的 JSON 结构损坏或字段类型错误", e)
    } catch (e: java.util.zip.ZipException) {
        throw IllegalArgumentException("ZIP 备份损坏，原数据未改动", e)
    } catch (e: java.io.IOException) {
        throw IllegalArgumentException("无法读取或暂存备份，请检查文件是否完整和设备剩余空间", e)
    } finally {
        staging.listFiles()?.forEach { it.delete() }
        staging.delete()
    }
}

internal fun journalJson(j: Journal): JSONObject = JSONObject()
    .put("id", j.id).put("title", j.title).put("date", j.date).put("place", j.place).put("body", j.body)
    .put("photos", JSONArray(j.photos)).put("tags", JSONArray(j.tags)).put("photoDescription", j.photoDescription)
    .put("createdAt", j.createdAt).put("updatedAt", j.updatedAt).put("wishId", j.wishId ?: JSONObject.NULL)
    .put("latitude", j.latitude ?: JSONObject.NULL).put("longitude", j.longitude ?: JSONObject.NULL)

internal fun journalFromJson(j: JSONObject): Journal = Journal(
    id = strictString(j, "id"), title = strictString(j, "title"), date = strictString(j, "date"),
    place = strictString(j, "place", ""), body = strictString(j, "body", ""), photos = stringList(j, "photos"), tags = stringList(j, "tags"),
    photoDescription = strictString(j, "photoDescription", ""), createdAt = strictLong(j, "createdAt"), updatedAt = strictLong(j, "updatedAt"), wishId = nullableString(j, "wishId"),
    latitude = nullableDouble(j, "latitude"), longitude = nullableDouble(j, "longitude"),
)

internal fun destinationJson(d: Destination): JSONObject = JSONObject()
    .put("id", d.id).put("name", d.name).put("region", d.region).put("tags", JSONArray(d.tags))
    .put("summary", d.summary).put("days", d.days).put("season", d.season).put("sourceUrl", d.sourceUrl).put("checkedAt", d.checkedAt)

internal fun destinationFromJson(d: JSONObject): Destination = Destination(
    id = strictString(d, "id"), name = strictString(d, "name"), region = strictString(d, "region", ""), tags = stringList(d, "tags"),
    summary = strictString(d, "summary", ""), days = strictString(d, "days", ""), season = strictString(d, "season", ""),
    sourceUrl = strictString(d, "sourceUrl", ""), checkedAt = strictString(d, "checkedAt", ""),
)

internal fun wishJson(w: Wish): JSONObject = JSONObject()
    .put("id", w.id).put("destination", destinationJson(w.destination)).put("query", w.query).put("reason", w.reason).put("notes", w.notes)
    .put("visited", w.visited).put("journalId", w.journalId ?: JSONObject.NULL).put("createdAt", w.createdAt)

internal fun wishFromJson(w: JSONObject): Wish {
    require(w.get("visited") is Boolean) { "收藏状态类型错误" }
    return Wish(id = strictString(w, "id"), destination = destinationFromJson(w.getJSONObject("destination")), query = strictString(w, "query", ""),
        reason = strictString(w, "reason", ""), notes = strictString(w, "notes", ""), visited = w.getBoolean("visited"), journalId = nullableString(w, "journalId"), createdAt = strictLong(w, "createdAt"))
}

internal fun strictString(json: JSONObject, key: String, default: String? = null): String {
    if (!json.has(key) && default != null) return default
    val value = json.get(key)
    require(value is String) { "字段 $key 必须是文字" }
    return value
}

internal fun stringList(json: JSONObject, key: String): List<String> {
    if (!json.has(key)) return emptyList()
    val array = json.getJSONArray(key)
    require(array.length() <= 1000) { "列表字段过长" }
    return (0 until array.length()).map { index ->
        val value = array.get(index)
        require(value is String) { "列表字段类型错误" }
        value
    }
}

private fun nullableString(json: JSONObject, key: String): String? = if (!json.has(key) || json.isNull(key)) null else strictString(json, key)

private fun nullableDouble(json: JSONObject, key: String): Double? {
    if (!json.has(key) || json.isNull(key)) return null
    val value = json.get(key)
    require(value is Number && value.toDouble().isFinite()) { "地图坐标必须是有效数值" }
    return value.toDouble()
}

private fun strictLong(json: JSONObject, key: String): Long {
    val value = json.get(key)
    require(value is Number && value !is Double && value !is Float) { "时间字段类型错误" }
    return value.toLong()
}
