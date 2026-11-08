package cn.edu.traveljournal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebTravelTest {
    private val config=AiConfig("https://example.org/v1","ai-only","model","小旅",true,"search-only")
    private val searchJson="""{"results":[{"title":"阿尔卑斯山徒步","url":"https://example.org/alps","content":"阿尔卑斯山提供山间步道和夏季徒步体验。"}]}"""
    private val modelJson="""{"understood":"想去山里徒步","items":[{"name":"阿尔卑斯山","region":"欧洲","sourceId":"S1","reason":"来源介绍了山间步道","caution":"确认天气","days":""}]}"""

    @Test fun realSearchStepPrecedesAiAndUsesSeparateCredential() = runBlocking {
        val steps=mutableListOf<String>()
        val search=WebSearchClient(AiTransport{settings,request->
            steps+="search";assertEquals("search-only",settings.apiKey);assertEquals(10,request.getInt("max_results"));assertEquals("advanced",request.getString("search_depth"));searchJson
        })
        val ai=AiClient(AiTransport{settings,request->
            steps+="ai";assertEquals("ai-only",settings.apiKey);assertTrue(request.toString().contains("https://example.org/alps"));modelJson
        })
        val result=OnlineTravelClient(search,ai).recommend(config,"想去欧洲山里徒步",emptySet())
        assertEquals(listOf("search","ai"),steps)
        assertEquals("阿尔卑斯山",result.destinations.single().name)
        assertEquals("https://example.org/alps",result.destinations.single().sourceUrl)
    }
    @Test fun inventedSourcesAndNamesAreNotPublished() {
        val sources=parseWebSources(searchJson)
        val unknown=JSONObject(modelJson).also{it.getJSONArray("items").getJSONObject(0).put("sourceId","invented")}.toString()
        assertTrue(parseOnlineRecommendations(unknown,sources,emptySet()).destinations.isEmpty())
        val invented=JSONObject(modelJson).also{it.getJSONArray("items").getJSONObject(0).put("name","不存在的秘境")}.toString()
        assertTrue(parseOnlineRecommendations(invented,sources,emptySet()).destinations.isEmpty())
        assertTrue(parseOnlineRecommendations(modelJson,sources,setOf("阿尔卑斯山")).destinations.isEmpty())
    }
    @Test fun unsafeAndDuplicateSearchUrlsAreDiscarded() {
        val raw="""{"results":[{"title":"a","content":"正文","url":"http://example.org"},{"title":"b","content":"正文","url":"https://user:secret@example.org"},{"title":"c","content":"正文","url":"https://example.org"},{"title":"d","content":"重复","url":"https://example.org"}]}"""
        assertEquals(1,parseWebSources(raw).size)
        assertThrows(AiException::class.java){parseWebSources("{bad")}
    }
    @Test fun missingSearchConfigurationDoesNotCallEitherService() {
        var calls=0
        val fake=AiTransport{_,_->calls++;error("must not run")}
        assertThrows(AiException::class.java){runBlocking{OnlineTravelClient(WebSearchClient(fake),AiClient(fake)).recommend(config.copy(searchApiKey=""),"想看海",emptySet())}}
        assertEquals(0,calls)
        assertFalse(config.toString().contains("search-only"))
        assertFalse(config.toString().contains("ai-only"))
    }
    @Test fun searchCancellationDoesNotContinueIntoAi() {
        var modelCalls=0
        val search=WebSearchClient(AiTransport{_,_->throw CancellationException("cancelled")})
        val ai=AiClient(AiTransport{_,_->modelCalls++;modelJson})
        assertThrows(CancellationException::class.java){runBlocking{OnlineTravelClient(search,ai).recommend(config,"看海",emptySet())}}
        assertEquals(0,modelCalls)
    }
    @Test fun noSearchResultsCannotBeReplacedWithModelMemory()=runBlocking {
        var modelCalls=0
        val search=WebSearchClient(AiTransport{_,_->"""{"results":[]}"""})
        val ai=AiClient(AiTransport{_,_->modelCalls++;modelJson})
        val result=OnlineTravelClient(search,ai).recommend(config,"任意地方",emptySet())
        assertTrue(result.destinations.isEmpty())
        assertEquals(0,modelCalls)
    }
    @Test fun conciseSearchAndFullRequirementsStaySeparate()=runBlocking {
        val search=WebSearchClient(AiTransport{_,request->assertEquals("春节去海南看海",request.getString("query"));searchJson})
        val ai=AiClient(AiTransport{_,request->assertTrue(request.toString().contains("预算：每人10000"));modelJson})
        OnlineTravelClient(search,ai).recommend(config,"春节去海南看海；预算：每人10000","春节去海南看海",emptySet())
        Unit
    }
    @Test fun emptySearchBroadensWithoutRepeatingOrCallingModelWithoutSources()=runBlocking {
        val seen=mutableListOf<String>();var modelCalls=0
        val search=WebSearchClient(AiTransport{_,request->seen+=request.getString("query");"""{"results":[]}"""})
        val ai=AiClient(AiTransport{_,_->modelCalls++;modelJson})
        val result=OnlineTravelClient(search,ai).recommend(config,"完整需求","春节去海南看海；出发地：重庆；天数：7；预算：10000",emptySet())
        assertEquals(listOf("春节去海南看海；出发地：重庆；天数：7；预算：10000","春节去海南看海","海南"),seen)
        assertTrue(result.destinations.isEmpty());assertEquals(0,modelCalls)
    }
    @Test fun unchangedSearchIsNotRepeated()=runBlocking {
        var calls=0
        val search=WebSearchClient(AiTransport{_,_->calls++;"""{"results":[]}"""})
        OnlineTravelClient(search,AiClient(AiTransport{_,_->error("no sources")})).recommend(config,"想看海",emptySet())
        assertEquals(1,calls)
    }
}
