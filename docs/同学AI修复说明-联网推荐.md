# 旅页 v1.3.0 联网推荐优化修改说明

**修改日期**：2026-11-07
**修改人**：协作开发 AI
**版本**：v1.3.0 → v1.3.0（内部优化，版本号不变）

---

## 一、问题描述

用户在"灵感"页使用联网推荐功能时，输入如"春节去海南看海"并配置出发地、天数、预算后，点击"联网搜索，寻找下一站"，AI 经常返回：

> "这次联网资料没有提供可靠的推荐。可以补充偏好或换个描述再试。"

**根本原因**：搜索词过长 + Tavily 搜索参数保守 + AI 过滤严格，导致搜索结果为空。

---

## 二、修改方案（A + D + E）

### 方案 A：优化 Tavily 搜索参数

| 参数 | 修改前 | 修改后 |
|---|---|---|
| `search_depth` | `"basic"` | `"advanced"` |
| `max_results` | `6` | `10` |

**效果**：搜索更深、返回更多候选资料。

---

### 方案 D：分离搜索词与完整需求

**修改前**：
```kotlin
// 所有信息拼接成一个长字符串，既用于搜索又用于 AI 整理
val request = "春节去海南看海；出发地：重庆北碚；天数：7；预算：每人10000元人民币..."
search.search(config, request)  // 长字符串直接发给 Tavily
```

**修改后**：
```kotlin
// 搜索词只包含用户原始描述
val searchQuery = "春节去海南看海"
// 完整需求包含所有约束，用于 AI 整理
val request = "春节去海南看海；出发地：重庆北碚；天数：7；预算：每人10000元..."

search.search(config, searchQuery)  // 精简词发给 Tavily
ai.call(config, request, ...)       // 完整需求发给 AI
```

**效果**：Tavily 收到简洁关键词，搜索命中率大幅提升。

---

### 方案 E：搜索失败时自动重试

当第一轮搜索结果为空时，自动用更宽泛的关键词重试（最多 2 次）：

```kotlin
// 第一轮：原始搜索词
"春节去海南看海；出发地：重庆北碚；天数：7；预算：每人10000元..."
  ↓ 失败
// 第二轮：移除约束，保留核心意图
"春节去海南看海"
  ↓ 失败
// 第三轮：提取核心地点
"海南"
```

**效果**：即使用户输入复杂，也能通过降级策略获取结果。

---

## 三、修改文件清单

### 1. `app/src/main/java/cn/edu/traveljournal/WebTravel.kt`

**修改内容**：

#### 1.1 `WebSearchClient.search()` 方法

```kotlin
// 修改前
suspend fun search(config:AiConfig, query:String): List<WebSource> {
    ...
    val request = JSONObject()
        .put("query", query)
        .put("search_depth", "basic")      // ← 浅层搜索
        .put("max_results", 6)             // ← 最多 6 条
        ...
}

// 修改后
suspend fun search(
    config: AiConfig,
    query: String,
    depth: String = "advanced",            // ← 默认深层搜索
    maxResults: Int = 10                   // ← 默认 10 条
): List<WebSource> {
    ...
    val request = JSONObject()
        .put("query", query)
        .put("search_depth", depth)
        .put("max_results", maxResults)
        ...
}

// 保留向后兼容的重载方法
suspend fun search(config:AiConfig, query:String): List<WebSource> =
    search(config, query, "advanced", 10)
```

#### 1.2 `OnlineTravelClient.recommend()` 方法

```kotlin
// 修改前
suspend fun recommend(
    config: AiConfig,
    query: String,              // 单一参数，既用于搜索又用于 AI
    excludedNames: Set<String>
): OnlineRecommendations {
    val searchQuery = (query + exclusions).take(5000)
    val sources = search.search(config, searchQuery)
    if (sources.isEmpty()) return 空结果
    ...
}

// 修改后
suspend fun recommend(
    config: AiConfig,
    query: String,              // 完整需求（给 AI 整理用）
    searchQuery: String,        // 精简搜索词（给 Tavily 用）
    excludedNames: Set<String>,
    retryCount: Int = 2         // 重试次数
): OnlineRecommendations {
    val sources = search.search(config, searchQuery, "advanced", 10)

    // 方案 E：搜索失败时自动重试
    if (sources.isEmpty() && retryCount > 0) {
        val fallbackQuery = simplifyQuery(searchQuery)
        return recommend(config, query, fallbackQuery, excludedNames, retryCount - 1)
    }
    ...
}

// 新增：搜索词简化方法
private fun simplifyQuery(query: String): String {
    // 移除"出发地：XX"、"天数：XX"、"预算：XX"等约束
    // 保留核心旅行意图
    ...
}

// 保留向后兼容的重载方法
suspend fun recommend(config:AiConfig, query:String, excludedNames:Set<String>) =
    recommend(config, query, query, excludedNames)
```

---

### 2. `app/src/main/java/cn/edu/traveljournal/DiscoverScreens.kt`

**修改内容**：

#### 2.1 `InspirationScreen` 中的 `recommend()` 函数

```kotlin
// 修改前
fun recommend(again: Boolean = false) {
    val request = listOf(
        query,
        origin.takeIf{it.isNotBlank()}?.let{"出发地：$it"},
        duration.takeIf{it.isNotBlank()}?.let{"天数：$it"},
        budget.takeIf{it.isNotBlank()}?.let{"预算：每人${it}元..."}
    ).filterNotNull().joinToString("；")

    OnlineTravelClient().recommend(config, request, omit)
}

// 修改后
fun recommend(again: Boolean = false) {
    // 方案 D：分离搜索词和完整需求
    val searchQuery = query.trim()  // 只包含用户原始描述
    val request = listOf(
        query,
        origin.takeIf{it.isNotBlank()}?.let{"出发地：$it"},
        duration.takeIf{it.isNotBlank()}?.let{"天数：$it"},
        budget.takeIf{it.isNotBlank()}?.let{"预算：每人${it}元..."}
    ).filterNotNull().joinToString("；")

    // 使用新的 recommend 签名
    OnlineTravelClient().recommend(
        config,
        request,      // 完整需求（给 AI 整理用）
        searchQuery,  // 精简搜索词（给 Tavily 用）
        omit
    )
}
```

---

### 3. `app/src/test/java/cn/edu/traveljournal/WebTravelTest.kt`

**修改内容**：

更新测试断言，匹配新的默认参数：

```kotlin
// 修改前
assertEquals(6, request.getInt("max_results"))

// 修改后
assertEquals(10, request.getInt("max_results"))
assertEquals("advanced", request.getString("search_depth"))
```

---

## 四、测试验证

### 构建结果

| 检查项 | 结果 |
|---|---|
| Kotlin 编译 | ✅ 通过 |
| APK 打包 | ✅ 通过 |
| 单元测试 | ✅ **82/82 通过** |
| Lint | ✅ **0 问题** |

### 测试覆盖

- `WebTravelTest.realSearchStepPrecedesAiAndUsesSeparateCredential` — 验证搜索参数
- `WebTravelTest.noSearchResultsCannotBeReplacedWithModelMemory` — 验证空结果处理
- `WebTravelTest.searchCancellationDoesNotContinueIntoAi` — 验证取消传播
- 其他 79 项测试 — 全部通过

---

## 五、向后兼容性

### API 兼容

- `WebSearchClient.search(config, query)` — 保留，内部调用新方法
- `OnlineTravelClient.recommend(config, query, excludedNames)` — 保留，内部调用新方法

**现有调用方无需修改**，新参数使用默认值。

### 数据兼容

- 无数据库结构变更
- 无备份格式变更
- 无用户数据影响

---

## 六、待验证事项

以下项目需要实际 API 密钥验证，本次修改未执行：

1. **Tavily 搜索效果** — 需配置 Tavily API 密钥，验证：
   - "春节去海南看海" 能否返回有效结果
   - 高级搜索深度是否返回更相关内容
   - 10 条结果是否比 6 条更有价值

2. **搜索词简化效果** — 需验证：
   - 长查询简化后是否保留核心意图
   - 重试机制是否能在首轮失败时成功

3. **AI 整理效果** — 需配置 AI API 密钥，验证：
   - 完整需求传递给 AI 后，推荐是否更精准
   - 出发地/预算等约束是否被正确考虑

4. **边界情况** — 需验证：
   - 用户只输入极短查询（如"看海"）时的行为
   - 网络超时时的错误提示
   - 连续快速点击"换一批"时的重试逻辑

---

## 七、交接文件

### 代码文件（已修改）

| 文件路径 | 修改类型 | 说明 |
|---|---|---|
| `app/src/main/java/cn/edu/traveljournal/WebTravel.kt` | 修改 | 核心搜索逻辑优化 |
| `app/src/main/java/cn/edu/traveljournal/DiscoverScreens.kt` | 修改 | 分离搜索词与需求 |
| `app/src/test/java/cn/edu/traveljournal/WebTravelTest.kt` | 修改 | 更新测试断言 |

### 文档文件（新增）

| 文件路径 | 说明 |
|---|---|
| `docs/修改说明-联网推荐优化.md` | 本修改说明文档 |

### 构建产物（可选）

| 文件路径 | 说明 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 测试安装包 |
| `build-modify.log` | 构建日志 |

---

## 八、后续建议

1. **监控搜索成功率** — 可在 app 中添加匿名统计，记录搜索成功/失败比例
2. **用户反馈机制** — 当推荐为空时，提供"反馈搜索质量"按钮
3. **搜索词智能改写** — 未来可用 AI 将用户长句改写为更适合搜索引擎的短查询
4. **缓存搜索结果** — 相同查询可缓存结果，减少 API 调用

---

**修改完成，等待共同开发者审核。**
