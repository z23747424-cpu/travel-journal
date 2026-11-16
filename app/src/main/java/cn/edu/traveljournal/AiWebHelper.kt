package cn.edu.traveljournal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

/**
 * AI 网页版助手：生成提示词、打开 AI 网页、读取并解析 AI 回复。
 *
 * 设计原则：网页版 AI 的输出格式并不稳定（会加寒暄、加代码围栏、把字段名换成中文、
 * 把来源写成裸域名），所以解析必须宽容，失败时要给出可操作的提示，而不是直接崩溃。
 */
object AiWebHelper {

    data class AiService(val id: String, val label: String, val url: String)

    // 1.7.1：通义 App 已更名「千问」，这里同步换成千问官网，并新增 DeepSeek。
    // 只改这一个列表即可，界面（灵感页的 FilterChip 与按钮文案）会自动跟随。
    val services: List<AiService> = listOf(
        AiService("kimi", "Kimi", "https://kimi.moonshot.cn/"),
        AiService("doubao", "豆包", "https://www.doubao.com/"),
        AiService("qianwen", "千问", "https://www.qianwen.com/"),
        AiService("deepseek", "DeepSeek", "https://chat.deepseek.com/"),
        AiService("chatgpt", "ChatGPT", "https://chatgpt.com/")
    )

    const val DEFAULT_SERVICE = "kimi"

    fun serviceLabel(id: String): String = services.firstOrNull { it.id == id }?.label ?: "Kimi"

    fun serviceUrl(id: String): String = services.firstOrNull { it.id == id }?.url ?: services.first().url

    /** 生成格式化提示词。 */
    fun generatePrompt(query: String, origin: String, duration: String, budget: String): String {
        val needs = listOf(
            query.trim(),
            origin.takeIf { it.isNotBlank() }?.let { "出发地：${it.trim()}" },
            duration.takeIf { it.isNotBlank() }?.let { "天数：${it.trim()}" },
            budget.takeIf { it.isNotBlank() }?.let { "预算：${it.trim()}" }
        ).filterNotNull().joinToString("；")

        return """
你是一个旅行规划助手。请根据以下用户需求，推荐 3-5 个适合的旅行目的地，并尽量给出真实的资料来源。

【用户需求】
$needs

【回复格式要求】
请严格按以下 JSON 格式回复，除了这段 JSON 不要输出任何其他内容（不要解释、不要问候、不要总结）：

```json
{
  "understood": "用一句话总结你理解的用户需求",
  "destinations": [
    {
      "name": "具体地点名称（如：三亚亚龙湾）",
      "region": "所属地区（如：海南三亚）",
      "reason": "推荐理由，对应需求（100 字以内）",
      "caution": "注意事项或待确认项（100 字以内，没有则留空）",
      "days": "建议停留天数（如：3-4 天，没有则留空）",
      "sourceUrl": "资料来源的完整网址（https 开头，没有可靠来源则留空）"
    }
  ]
}
```

【重要规则】
1. 只返回 JSON，不要任何其他文字，也不要输出思考过程
2. 每个地点尽量给出真实资料来源（sourceUrl），没有就留空字符串，不要编造网址
3. 如果资料不足，可以返回空列表：{"understood":"...","destinations":[]}
4. 地点名称要具体（如"三亚亚龙湾"而非"海南"）
5. 推荐理由要对应需求，说明为什么适合
6. 不要推荐需要签证、季节封闭或明显不安全的地方而不做说明
        """.trimIndent()
    }

    /** 复制提示词到剪贴板。 */
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("旅行推荐提示词", text))
    }

    /** 读取剪贴板纯文本，没有内容时返回空串。 */
    fun clipboardText(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    /** 打开 AI 网页；没有可用浏览器时抛异常由界面提示。 */
    fun openAiWeb(context: Context, aiService: String = DEFAULT_SERVICE) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serviceUrl(aiService)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 复制提示词并打开 AI 网页。 */
    fun copyAndOpenAi(context: Context, prompt: String, aiService: String = DEFAULT_SERVICE) {
        copyToClipboard(context, prompt)
        openAiWeb(context, aiService)
    }

    /**
     * 解析 AI 回复的 JSON。允许前后夹杂说明文字、代码围栏、中文句号等。
     * 解析不出结构化内容时抛 [AiException]，消息可直接展示给用户。
     */
    fun parseAiResponse(
        jsonText: String,
        query: String = "",
        excludedNames: Set<String> = emptySet()
    ): OnlineRecommendations {
        val payload = extractJsonPayload(jsonText)
        val json = try {
            JSONObject(payload)
        } catch (e: Exception) {
            throw AiException("解析 AI 回复失败：这段文字里没有合法的 JSON。\n请只复制以 { 开头、以 } 结尾的那一整段回复。")
        }

        val understood = json.optString("understood", "").take(3000)
        val items = destinationsArray(json)

        val excluded = excludedNames.map { it.trim().lowercase() }.toSet()
        val seenNames = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()
        val destinations = mutableListOf<Destination>()
        val recommendations = mutableListOf<Recommendation>()

        for (i in 0 until items.length()) {
            if (destinations.size >= 8) break
            val item = items.optJSONObject(i) ?: continue
            val name = pick(item, "name", "place", "spot", "title", "destination", "名称", "地点", "目的地")
                .take(200)
            if (name.isBlank()) continue
            val key = name.lowercase()
            if (key in excluded || key in seenNames) continue
            val sourceUrl = normalizeHttpUrl(
                pick(item, "sourceUrl", "source_url", "url", "link", "source", "来源", "链接", "网址")
            )
            val id = webId(name, sourceUrl)
            if (!seenIds.add(id)) continue
            seenNames += key

            val reason = pick(item, "reason", "why", "summary", "intro", "推荐理由", "理由").take(2000)
            val caution = pick(item, "caution", "note", "notes", "tips", "warning", "需要注意", "注意事项").take(2500)
            val days = pick(item, "days", "duration", "stay", "天数", "建议天数").take(300)
            val region = pick(item, "region", "area", "location", "city", "province", "地区", "所在地").take(300)

            destinations += Destination(
                id = id,
                name = name,
                region = region,
                tags = listOf("AI 网页推荐"),
                summary = reason.ifBlank { "AI 网页版推荐" },
                days = days,
                sourceUrl = sourceUrl,
                checkedAt = Instant.now().toString()
            )
            recommendations += Recommendation(id, reason, caution)
        }

        return OnlineRecommendations(destinations, RecommendationResult(understood, recommendations))
    }

    /** 检查一段文字看起来是否像 AI 返回的推荐 JSON。 */
    fun isAiResponse(text: String): Boolean {
        if (text.isBlank()) return false
        val hasList = Regex("\"?destinations\"?\\s*:").containsMatchIn(text) ||
            Regex("\"(名称|地点|目的地)\"\\s*:").containsMatchIn(text)
        val hasName = Regex("\"(name|place|spot|名称|地点)\"\\s*:").containsMatchIn(text)
        return hasList && hasName
    }

    /** 抽出文本中的第一个 JSON 对象/数组，容忍前后说明文字与 ``` 围栏。 */
    internal fun extractJsonPayload(text: String): String {
        var s = text.replace("\uFEFF", "").trim()
        if (s.isBlank()) throw AiException("请先粘贴 AI 的回复")
        // 去掉代码围栏标记（可能出现在任意位置，也可能是 ```JSON）
        s = s.replace(Regex("```[A-Za-z]*"), "").replace("```", "").trim()

        val objStart = s.indexOf('{')
        // 有些 AI 只回一个数组：[{...},{...}]，先把数组包成对象再解析
        val firstMeaningful = s.firstOrNull { !it.isWhitespace() }
        if (firstMeaningful == '[') {
            val end = s.lastIndexOf(']')
            if (end > 0) return "{\"destinations\":${s.substring(s.indexOf('['), end + 1)}}"
        }
        if (objStart >= 0) {
            val end = matchingBrace(s, objStart)
            val candidate = when {
                end > objStart -> s.substring(objStart, end + 1)
                else -> s.substring(objStart) // 括号没闭合，交给 JSONObject 报错
            }
            if (candidate.isNotBlank()) return candidate
        }

        val arrStart = s.indexOf('[')
        if (arrStart >= 0) {
            val end = s.lastIndexOf(']')
            if (end > arrStart) return "{\"destinations\":${s.substring(arrStart, end + 1)}}"
        }

        throw AiException("没有找到 JSON 内容。\n请复制 AI 回复里从 { 开始到 } 结束的整段内容。")
    }

    /** 只接受 http/https 且带域名的地址，其余一律视为没有来源。 */
    internal fun normalizeHttpUrl(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        val explicit = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).find(text)?.value
        val candidate = if (explicit != null) {
            explicit.trimEnd('.', ',', ';', ':', '。', '，', '；', '）', ')')
        } else {
            val bare = text.trim(' ', '\t', '\n', '。', '，', '；', '）', '(', ')', '（', '<', '>', '"', '\'', '.')
            val looksLikeDomain = Regex("^[A-Za-z0-9][A-Za-z0-9.-]*\\.[A-Za-z]{2,}(/\\S*)?$").matches(bare)
            if (!looksLikeDomain) return ""
            "https://$bare"
        }
        if (candidate.isBlank()) return ""
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return ""
        if (uri.scheme?.lowercase() !in listOf("http", "https")) return ""
        val host = uri.host
        if (host.isNullOrBlank() || !host.contains('.')) return ""
        return candidate
    }

    private fun destinationsArray(json: JSONObject): JSONArray {
        val raw = json.opt("destinations")
        return when (raw) {
            is JSONArray -> raw
            is JSONObject -> JSONArray().put(raw)
            null, JSONObject.NULL -> throw AiException(
                "AI 回复里没有 destinations 列表。\n请让 AI 按提示词里的 JSON 格式重新回复一次，再复制整段回复。"
            )
            else -> throw AiException("destinations 字段格式不对，应该是数组：[{...},{...}]")
        }
    }

    private fun pick(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!obj.has(k)) continue
            val v = obj.opt(k) ?: continue
            if (v === JSONObject.NULL) continue
            val text = when (v) {
                is String -> v
                is Number, is Boolean -> v.toString()
                is JSONArray -> (0 until v.length()).mapNotNull { i -> v.opt(i)?.toString()?.takeIf { it != "null" } }.joinToString("、")
                else -> "" // 嵌套对象（如 {"zh":"..."}）不猜，交给下一个候选键
            }
            if (text.isNotBlank() && text != "null") return text.trim()
        }
        return ""
    }

    private fun webId(name: String, sourceUrl: String): String =
        "web-" + MessageDigest.getInstance("SHA-256")
            .digest((name + "\n" + sourceUrl).toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    /** 从 start 处的 { 开始做字符串感知的括号配对，返回匹配的 } 下标，失败返回 -1。 */
    private fun matchingBrace(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}