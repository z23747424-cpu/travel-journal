package cn.edu.traveljournal

import org.junit.Assert.*
import org.junit.Test

/**
 * AI 网页版回复的解析回归测试。
 * 真实网页版 AI 会加寒暄、加代码围栏、换中文字段名，这里把这些情况都钉住。
 */
class AiWebTest {

    @Test fun promptAsksForStrictJsonButAllowsEmptyList() {
        val prompt = AiWebHelper.generatePrompt("海边发呆", "西安", "4", "3000 元")
        assertTrue(prompt.contains("\"destinations\""))
        assertTrue(prompt.contains("\"sourceUrl\""))
        assertTrue(prompt.contains("\"understood\""))
        assertTrue(prompt.contains("海边发呆"))
        assertTrue(prompt.contains("出发地：西安"))
        assertTrue(prompt.contains("天数：4"))
        // 允许 AI 在资料不足时返回空列表，避免它编造地点
        assertTrue(prompt.contains("\"destinations\":[]"))
    }

    @Test fun parsesFencedJsonSurroundedByChatter() {
        val reply = """
            好的，这是我根据你的需求整理的目的地：
            ```json
            {
              "understood": "想在秋天找个人少的海边",
              "destinations": [
                {"name":"宁德霞浦","region":"福建宁德","reason":"滩涂日落","caution":"关注潮汐","days":"2-3 天","sourceUrl":"https://example.org/xiapu"}
              ]
            }
            ```
            希望对你安排行程有帮助。
        """.trimIndent()
        val result = AiWebHelper.parseAiResponse(reply)
        assertEquals("想在秋天找个人少的海边", result.result.understood)
        assertEquals(1, result.destinations.size)
        assertEquals("宁德霞浦", result.destinations.first().name)
        assertEquals("https://example.org/xiapu", result.destinations.first().sourceUrl)
        assertEquals("关注潮汐", result.result.items.first().caution)
        assertEquals("2-3 天", result.destinations.first().days)
    }

    @Test fun missingDestinationsGivesActionableMessage() {
        val e = assertThrows(AiException::class.java) {
            AiWebHelper.parseAiResponse("{\"understood\":\"看不懂的需求\"}")
        }
        assertTrue(e.message!!.contains("destinations"))
    }

    @Test fun emptyDestinationsIsNotAnError() {
        val result = AiWebHelper.parseAiResponse("{\"understood\":\"资料不足\",\"destinations\":[]}")
        assertEquals("资料不足", result.result.understood)
        assertTrue(result.destinations.isEmpty())
    }

    @Test fun acceptsChineseFieldNames() {
        val reply = """
            {
              "understood": "想看雪山",
              "destinations": [
                {"名称":"雨崩村","地区":"云南迪庆","推荐理由":"梅里雪山徒步","注意事项":"海拔较高","天数":"4 天","来源":"example.org/yubeng"}
              ]
            }
        """.trimIndent()
        val d = AiWebHelper.parseAiResponse(reply).destinations.single()
        assertEquals("雨崩村", d.name)
        assertEquals("云南迪庆", d.region)
        assertEquals("梅里雪山徒步", d.summary)
        assertEquals("4 天", d.days)
        assertEquals("https://example.org/yubeng", d.sourceUrl)
    }

    @Test fun bareArrayReplyIsWrapped() {
        val reply = """[{"name":"婺源","region":"江西","reason":"油菜花"}]"""
        val d = AiWebHelper.parseAiResponse(reply).destinations.single()
        assertEquals("婺源", d.name)
    }

    @Test fun duplicateNamesCollapseIntoOneCard() {
        val reply = """
            {"destinations":[
              {"name":"霞浦","reason":"一个理由","sourceUrl":"https://a.example/x"},
              {"name":"霞浦","reason":"另一处来源","sourceUrl":"https://b.example/y"}
            ]}
        """.trimIndent()
        assertEquals(1, AiWebHelper.parseAiResponse(reply).destinations.size)
    }

    @Test fun excludedNamesAreSkipped() {
        val reply = """{"destinations":[{"name":"霞浦","reason":"x"}]}"""
        assertTrue(AiWebHelper.parseAiResponse(reply, "海边", setOf("霞浦")).destinations.isEmpty())
    }

    @Test fun dropsNonHttpSourceAndKeepsCard() {
        val reply = """{"destinations":[{"name":"某个地方","reason":"x","sourceUrl":"javascript:alert(1)"}]}"""
        val d = AiWebHelper.parseAiResponse(reply).destinations.single()
        assertEquals("", d.sourceUrl)
    }

    @Test fun normalizesUrls() {
        assertEquals("https://example.org/a", AiWebHelper.normalizeHttpUrl("https://example.org/a"))
        assertEquals("https://example.org/a", AiWebHelper.normalizeHttpUrl("example.org/a"))
        assertEquals("https://example.org/a", AiWebHelper.normalizeHttpUrl("（https://example.org/a）。"))
        assertEquals("", AiWebHelper.normalizeHttpUrl("mailto:hi@example.org"))
        assertEquals("", AiWebHelper.normalizeHttpUrl(""))
    }

    @Test fun blankNameCardsAreSkipped() {
        val reply = """{"destinations":[{"name":"   ","reason":"x"},{"name":"乌镇","reason":"y"}]}"""
        assertEquals(listOf("乌镇"), AiWebHelper.parseAiResponse(reply).destinations.map { it.name })
    }

    @Test fun detectsAiReplyInClipboardText() {
        assertTrue(AiWebHelper.isAiResponse("""{"understood":"a","destinations":[{"name":"霞浦"}]}"""))
        assertTrue(AiWebHelper.isAiResponse("""{"名称":"霞浦","推荐理由":"x"}"""))
        assertFalse(AiWebHelper.isAiResponse("明天去哪玩？"))
        assertFalse(AiWebHelper.isAiResponse(""))
    }

    /** 1.7.1：可选 AI 网页改成 Kimi / 豆包 / 千问 / DeepSeek / ChatGPT，通义已下线。 */
    @Test fun servicesOfferQianwenAndDeepSeekButNotTongyi() {
        val ids = AiWebHelper.services.map { it.id }
        assertTrue(ids.contains("qianwen"))
        assertTrue(ids.contains("deepseek"))
        assertFalse(ids.contains("tongyi"))
        assertEquals(AiWebHelper.services.size, ids.distinct().size)

        // 每个服务都要有可打开的 https 地址，否则按钮点了没反应
        AiWebHelper.services.forEach { s ->
            assertTrue("${s.label} 缺少 https 地址", s.url.startsWith("https://"))
            assertEquals(s.label, AiWebHelper.serviceLabel(s.id))
            assertEquals(s.url, AiWebHelper.serviceUrl(s.id))
        }
        assertEquals("https://www.qianwen.com/", AiWebHelper.serviceUrl("qianwen"))
        assertEquals("https://chat.deepseek.com/", AiWebHelper.serviceUrl("deepseek"))
        // 未知/已下线的 id 要回退到第一个服务，不能崩
        assertEquals(AiWebHelper.services.first().url, AiWebHelper.serviceUrl("tongyi"))
    }
}