package cn.edu.traveljournal

/**
 * AI 网页版推荐的解析结果。
 *
 * 1.7.0 起移除了 Tavily 联网搜索（WebSearchClient / OnlineTravelClient 已删除），
 * 推荐内容全部来自用户手动粘贴的 AI 网页回复，解析逻辑见 [AiWebHelper.parseAiResponse]。
 */
data class OnlineRecommendations(val destinations: List<Destination>, val result: RecommendationResult)