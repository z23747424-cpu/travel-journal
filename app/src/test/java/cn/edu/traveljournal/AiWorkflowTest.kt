package cn.edu.traveljournal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AiWorkflowTest {
    private val config = AiConfig("https://example.org/v1", "private-test-token", "example-model", "小旅", true)

    @Test fun semanticSearchCoversAllBatchesThenReranksOnlyVerifiedCandidates() = runBlocking {
        val requests = mutableListOf<JSONObject>()
        val client = AiClient(AiTransport { _, request ->
            requests += request
            when (requests.size) {
                1 -> "{\"items\":[{\"id\":\"0\",\"reason\":\"海边\"},{\"id\":\"fake\"}]}"
                2 -> "{\"items\":[{\"id\":\"20\",\"reason\":\"日落\"}]}"
                else -> "{\"items\":[{\"id\":\"20\",\"reason\":\"更相关\"},{\"id\":\"0\",\"reason\":\"相关\"},{\"id\":\"2\",\"reason\":\"编造候选\"}]}"
            }
        })
        val journals = (0..24).map { Journal(id = "$it", title = "旅行$it", body = "海边") }
        val result = client.search(config, "看海", journals)
        assertEquals(listOf("20", "0"), result.map { it.id })
        assertEquals(3, requests.size)
        val firstPayload = JSONObject(requests[0].getJSONArray("messages").getJSONObject(1).getString("content"))
        val secondPayload = JSONObject(requests[1].getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(20, firstPayload.getJSONArray("journals").length())
        assertEquals(5, secondPayload.getJSONArray("journals").length())
        assertFalse(requests.toString().contains(config.apiKey))
    }

    @Test fun searchOverBudgetFailsExplicitlyWithoutSendingPartialData() = runBlocking {
        var calls = 0
        val client = AiClient(AiTransport { _, _ -> calls++; "{\"items\":[]}" })
        try {
            client.search(config, "海边", (0..100).map { Journal(id = "$it", title = "记录", body = "海边") })
            fail("应提示用户缩小范围")
        } catch (e: AiException) { assertTrue(e.message!!.contains("100")) }
        assertEquals(0, calls)
    }

    @Test fun cancellationIsNotConvertedIntoAnApiError() = runBlocking {
        val client = AiClient(AiTransport { _, _ -> throw CancellationException("cancel") })
        try { client.test(config); fail("应传播取消") }
        catch (_: CancellationException) { /* expected */ }
    }

    @Test fun aiRequiresConsentBeforeSendingData() = runBlocking {
        var called = false
        val client = AiClient(AiTransport { _, _ -> called = true; "连接成功" })
        try { client.test(config.copy(consent = false)); fail("必须先同意发送数据") }
        catch (e: AiException) { assertTrue(e.message!!.contains("同意")) }
        assertFalse(called)
    }

    @Test fun photoCallRejectsMoreThanFourBeforeReadingOrSendingAnything() = runBlocking {
        var called = false
        val client = AiClient(AiTransport { _, _ -> called = true; "{}" })
        try { client.describe(config, (1..5).map { File("unused-$it.jpg") }, ""); fail("单次最多四张") }
        catch (e: AiException) { assertTrue(e.message!!.contains("4")) }
        assertFalse(called)
    }

    @Test fun recommendationExtractsConditionsBeforeRankingAndRemovesExcludedCatalogItems() = runBlocking {
        val requests = mutableListOf<JSONObject>()
        val client = AiClient(AiTransport { _, request ->
            requests += request
            if (requests.size == 1) "{\"understood\":\"国内两天看海，预算和交通待确认\",\"region\":\"\",\"excluded\":[],\"preferences\":[\"海边\"],\"constraints\":[\"两天\"]}"
            else "{\"understood\":\"国内两天看海，预算和交通待确认\",\"items\":[{\"id\":\"a\",\"reason\":\"滨海体验\"},{\"id\":\"b\"},{\"id\":\"fake\"}]}"
        })
        val result = client.recommend(config, "两天看海", listOf(Destination(id = "a", name = "青岛"), Destination(id = "b", name = "厦门")), setOf("b"))
        assertEquals(listOf("a"), result.items.map { it.id })
        assertEquals(2, requests.size)
        val payload = JSONObject(requests[1].getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(listOf("a"), (0 until payload.getJSONArray("destinations").length()).map { payload.getJSONArray("destinations").getJSONObject(it).getString("id") })
    }

    @Test fun unsupportedRegionProducesNoInventedRecommendation() = runBlocking {
        var calls = 0
        val client = AiClient(AiTransport { _, _ ->
            calls++
            "{\"understood\":\"日本赏樱\",\"region\":\"日本\",\"excluded\":[],\"preferences\":[\"樱花\"],\"constraints\":[]}"
        })
        val result = client.recommend(config, "去日本赏樱", listOf(Destination(id = "a", name = "青岛", region = "山东")), emptySet())
        assertTrue(result.items.isEmpty())
        assertTrue(result.understood.contains("未覆盖"))
        assertEquals(1, calls)
    }

    @Test fun provinceSuffixIsAnAliasButCityNamesDoNotMatchAWholeProvince() {
        assertTrue(destinationInRegion(Destination(name = "杭州", region = "浙江"), "浙江省"))
        assertTrue(destinationInRegion(Destination(name = "青岛", region = "山东"), "青岛市"))
        assertFalse(destinationInRegion(Destination(name = "长春", region = "吉林"), "吉林市"))
        assertFalse(destinationInRegion(Destination(name = "拉萨", region = "西藏"), "西"))
        assertTrue(destinationInRegion(Destination(name = "桂林", region = "广西"), "广西壮族自治区"))
    }

    @Test fun httpRequestsDisableRedirectsAndNeverExposeProviderErrors() = runBlocking {
        val connection = FakeConnection(302)
        val client = AiClient(HttpsAiTransport { connection })
        try { client.test(config); fail("必须拒绝重定向") }
        catch (e: AiException) {
            assertTrue(e.message!!.contains("重定向"))
            assertFalse(e.message!!.contains(config.apiKey))
        }
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
        assertEquals("Bearer ${config.apiKey}", connection.getRequestProperty("Authorization"))
    }

    @Test fun cancellingTheRequestDisconnectsAnActiveConnection() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : FakeConnection(200) {
            override fun getOutputStream(): OutputStream {
                entered.countDown()
                released.await(10, TimeUnit.SECONDS)
                return super.getOutputStream()
            }
            override fun disconnect() { super.disconnect(); released.countDown() }
        }
        val client = AiClient(HttpsAiTransport { connection })
        val job = launch { client.test(config) }
        val started = withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) }
        assertTrue(started)
        job.cancel()
        job.join()
        assertTrue(connection.disconnected)
    }

    @Test fun invalidProviderJsonRemainsAnErrorAndSuccessfulChatIsParsed() = runBlocking {
        val invalid = AiClient(HttpsAiTransport { FakeConnection(200, "not-json-private-test-token") })
        try { invalid.test(config); fail("格式错误必须失败") }
        catch (e: AiException) { assertFalse(e.message!!.contains(config.apiKey)) }
        val valid = AiClient(HttpsAiTransport { FakeConnection(200, "{\"choices\":[{\"message\":{\"content\":\"你好\"}}]}") })
        assertTrue(valid.test(config).contains("文本连接成功"))
    }

    private open class FakeConnection(private val status: Int, private val response: String = "") : HttpURLConnection(URL("https://example.org")) {
        @Volatile var disconnected = false
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun connect() { }
        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = ByteArrayInputStream(response.toByteArray())
    }
}
