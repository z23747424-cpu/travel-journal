# 旅页 AI 协作开发指南

> 本文档供协作开发者的 AI 助手阅读。请完整阅读后再接手开发任务。

---

## 一、项目概况

**旅页**是一个 Android 旅行手账应用，使用 Kotlin + Jetpack Compose 开发。

| 项目 | 值 |
|---|---|
| 包名 | `cn.edu.traveljournal`（正式版）/ `cn.edu.traveljournal.dev`（开发版） |
| 最低 API | 26（Android 8.0） |
| 目标 API | 35 |
| 当前版本 | v1.7.1（versionCode=10） |
| 源码目录 | `app/src/main/java/cn/edu/traveljournal/` |
| 地图素材 | `app/src/main/assets/atlas/`（城市级，391 个城市） |
| 旧地图资源 | `app/src/main/assets/map/`（保留作历史比较，不作为当前运行入口） |

### 核心功能

- **首页地图**：原生 Canvas 绘制中国城市轮廓，支持缩放、平移、选点
- **手账记录**：照片、文字、标签、坐标、去过/未去过状态
- **旅行计划**：逐日行程编辑，AI 辅助生成草案
- **想去清单**：收藏目的地，关联手账
- **搜索**：本地关键词搜索 + AI 语义搜索
- **灵感推荐**：AI 网页版推荐（复制 AI 网页回复 → 粘贴 → 解析成卡片 → 一键开始写计划）。v1.7.0 起已彻底移除 Tavily 联网搜索与「让精灵找下一站」，应用不再发出任何搜索请求
- **备份恢复**：ZIP 格式，含手账、照片、想去清单

### 产品约束（不可违反）

1. 完整中国底图默认离线可见，支持缩放和平移
2. 去过的区域提亮，未去过的区域暗色；想去计划和教学示例不算实际足迹
3. 用户自行配置 AI；灵感推荐通过网页版 AI（用户在浏览器自行搜索再粘贴解析）实现，不再内置任何联网搜索接口，也不恢复内置目的地白名单
4. 保留旧笔记、图片、坐标、状态和备份恢复的兼容性
5. 搜索有独立界面，保持简洁杂志风排版
6. 标记保持小巧，触控区域至少 48dp
7. 不把历史 experiments/ 固定目录基线当成当前联网推荐准确率

---

## 二、开发环境配置

### 必须的工具

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17 或 21 | 推荐 Adoptium Temurin |
| Android SDK | Platform 35, Build Tools 35.0.0 | 含 platform-tools |
| Gradle Wrapper | 8.9 | 使用仓库自带的 wrapper |
| AGP | 8.7.3 | Android Gradle Plugin |
| Kotlin | 2.0.21 | 应用源码版本 |

### 环境变量

```
JAVA_HOME=D:\app_files\Adoptium_files
ANDROID_HOME=D:\Android\Sdk
ANDROID_SDK_ROOT=D:\Android\Sdk
```

### ⚠️ 关键：中文路径问题

**源码路径包含中文和空格**（如 `D:\school_study\machine learning\app_develope\`），这会导致 Kotlin 编译器读取字符串字面量时出错。

**解决方案**：将项目复制到纯英文路径再构建。

```powershell
# 复制到纯英文路径
Copy-Item "D:\school_study\machine learning\app_develope\v1.5\lvye-source-1.5.0" "D:\TravelBuild" -Recurse -Force

# 在英文路径下构建
cd D:\TravelBuild
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

### Gradle 国内镜像

官方 Gradle 下载在国内很慢，已修改为腾讯云镜像：

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

如果镜像失效，可尝试：
- `https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.9-bin.zip`
- 或手动下载后放到 `~/.gradle/wrapper/dists/` 下

### 构建命令

```powershell
# 完整构建（编译 + 测试 + lint）
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain

# 仅编译
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain

# 仅打包 APK
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain

# 仅测试
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain

# 仅 lint
.\gradlew.bat :app:lintDebug --no-daemon --console=plain
```

### 项目提供的 Windows 构建脚本

```powershell
.\scripts\build-windows.ps1 -Tasks @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
```

该脚本会自动处理中文路径问题（复制到英文临时目录构建）。

---

## 三、代码结构

### 主要 Kotlin 文件

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 导航、草稿、保存、页面状态 |
| `Models.kt` | 数据模型：Journal, Destination, Wish, AiConfig 等 |
| `NativeModels.kt` | 旅行计划相关模型：TravelPlan, PlanDay 等 |
| `JournalRepository.kt` | SQLite 数据库、照片管理、加密配置 |
| `Backup.kt` | JSON/ZIP 备份恢复、导入冲突处理 |
| `MapScreens.kt` | 地图首页、地点菜单、选点弹窗 |
| `MapSurface.kt` | WebView 生命周期、JS 桥（旧版，保留） |
| `NativeTravelMapView.kt` | **原生地图视图**（当前使用） |
| `ProvinceMapView.kt` | 省级地图绘制 |
| `CityAtlas.kt` | 城市图集数据加载 |
| `NativeInspirationScreen.kt` | **灵感页**（当前使用，含 AI 网页版推荐） |
| `DiscoverScreens.kt` | 旧版灵感页（保留，当前未使用） |
| `JournalScreens.kt` | 手账详情、编辑、图片选择 |
| `SettingsScreen.kt` | 模型/搜索配置、导入导出 |
| `Search.kt` | 关键词搜索、AI 语义检索 |
| `AiClient.kt` | AI 服务调用（Chat Completions 协议） |
| `WebTravel.kt` | Tavily 联网搜索 + AI 整理 |
| `NativePlanningAi.kt` | AI 辅助生成旅行计划草案 |
| `NativePlanEditor.kt` | 旅行计划编辑器 |
| `Ui.kt` | 原生主题和常用排版组件 |
| `AiWebHelper.kt` | **AI 网页版推荐助手**（v1.6 新增） |

### 测试文件

| 文件 | 测试内容 |
|---|---|
| `CoreLogicTest.kt` | 核心业务逻辑 |
| `JournalRepositoryTest.kt` | 数据仓库（含插画、拖动、去过状态） |
| `MapLogicTest.kt` | 地图逻辑（坐标校验、载荷） |
| `MapResourcesTest.kt` | 地图资源（本地资源、透明图片） |
| `NativeMapRenderTest.kt` | 原生地图渲染 |
| `NativeMapInteractionTest.kt` | 原生地图交互 |
| `NativeAtlasTest.kt` | 原生图集 |
| `RealAtlasCanvasTest.kt` | 真实图集画布 |
| `CityAtlasMaterialTest.kt` | 城市图集素材 |
| `WebTravelTest.kt` | 联网搜索 |
| `AiWorkflowTest.kt` | AI 工作流 |
| `ActivitySmokeTest.kt` | 基础启动 |

---

## 四、当前版本功能详情

### v1.6 新增：AI 网页版推荐

**背景**：Tavily API 在国内访问慢、易超时，且需要付费。改为让用户使用 AI 网页版（Kimi/豆包/千问/DeepSeek/ChatGPT）获取推荐，完全免费。

**可选服务（1.7.1）**：列表只维护在 `AiWebHelper.services` 一处，界面自动跟随。

| id | 显示名 | 网址 |
|---|---|---|
| `kimi` | Kimi | `https://kimi.moonshot.cn/` |
| `doubao` | 豆包 | `https://www.doubao.com/` |
| `qianwen` | 千问 | `https://www.qianwen.com/` |
| `deepseek` | DeepSeek | `https://chat.deepseek.com/` |
| `chatgpt` | ChatGPT | `https://chatgpt.com/` |

通义 App 已于 2025-11 更名「千问」（域名 `qianwen.com`），故 1.7.1 起不再提供 `tongyi`；未知 id 由 `serviceUrl()` 回退到列表第一项，不会崩。

**实现位置**：
- `AiWebHelper.kt` — 提示词生成、剪贴板操作、网页跳转、回复解析
- `NativeInspirationScreen.kt` — UI 入口和结果展示

**使用流程**：
1. 用户在"找下一站"页面填写需求
2. 点击"用 AI 网页版推荐（免费，无需 API）"
3. 点击"生成提示词并打开 AI 网页"→ 提示词复制到剪贴板 + 打开 Kimi
4. 用户在 Kimi 中粘贴提问
5. 复制 Kimi 的 JSON 回复
6. 回到 app 粘贴到输入框，点击"解析回复"
7. 显示目的地卡片

**提示词格式**：要求 AI 返回严格 JSON，包含 `understood` 和 `destinations` 数组，每个目的地有 `name`、`region`、`reason`、`caution`、`days`、`sourceUrl`。

---

## 五、遇到的困难与解决方案

### 困难 1：中文路径导致 Kotlin 编译失败

**现象**：
```
e: file:///D:/school_study/machine%20learning/.../DiscoverScreens.kt:172:47
Unresolved reference '生成提示词'.
Unsupported [literal prefixes and suffixes].
```

**原因**：路径中的空格和中文导致 Kotlin 编译器在读取源码文件时，URL 编码破坏了字符串字面量的解析。

**解决方案**：
- 将项目复制到纯英文路径（如 `D:\TravelBuild`）再构建
- 或使用项目提供的 `scripts/build-windows.ps1` 脚本（自动处理）

**教训**：不要直接在中文路径下运行 Gradle 构建。

---

### 困难 2：Gradle 下载超时

**现象**：
```
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
............10%...
Exception: Connect timed out
```

**原因**：Gradle 官方服务器在国内访问慢。

**解决方案**：修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 为腾讯云镜像：
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

**教训**：首次构建前就修改镜像，避免等待超时。

---

### 困难 3：构建命令超时（120 秒限制）

**现象**：使用 `run_command` 执行 Gradle 构建时，命令在 120 秒后超时，但构建实际还在后台运行。

**原因**：首次构建需要下载大量依赖（AGP、Kotlin、Compose BOM 等），耗时远超 120 秒。

**解决方案**：使用 `Start-Process` 启动后台进程，将输出重定向到日志文件，然后轮询检查日志。

```powershell
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "gradlew.bat :app:assembleDebug > build.log 2>&1" -NoNewWindow
# 然后定期检查 build.log 的内容
```

**教训**：不要直接用 `run_command` 执行 Gradle 构建，要用后台进程 + 日志轮询。

---

### 困难 4：改错文件（DiscoverScreens vs NativeInspirationScreen）

**现象**：代码修改成功、编译通过、APK 生成，但手机上看不到新功能。

**原因**：v1.5 有两个灵感页实现：
- `DiscoverScreens.kt` — 旧版（当前未使用）
- `NativeInspirationScreen.kt` — 新版（当前使用）

我修改了旧版文件，但 app 实际使用的是新版文件。

**解决方案**：
- 先确认 app 实际使用的 Composable 函数
- 在 `MainActivity.kt` 中查找导航路由，确认哪个屏幕被调用
- 修改正确的文件

**教训**：修改 UI 前，先确认 app 实际使用的是哪个 Composable 函数。检查 `MainActivity.kt` 中的导航配置。

---

### 困难 5：APK 签名冲突无法覆盖安装

**现象**：
```
软件包与现有软件包存在冲突
```

**原因**：原 v1.5.0 使用原作者的 debug 签名，新构建的 APK 使用本机 debug 签名，两者不同。Android 不允许不同签名的同名包覆盖安装。

**解决方案**：
- **方案 A**：修改 `applicationId` 为 `cn.edu.traveljournal.dev`，让两个版本共存
- **方案 B**：卸载旧版后安装新版（会丢失数据）

**教训**：协作开发时，修改 `applicationId` 加 `.dev` 后缀是最安全的做法。

---

### 困难 6：Tavily API 国内访问慢且易超时

**现象**：用户点击"联网搜索"后等待很长时间，经常超时失败。

**原因**：
- Tavily 服务器在国外，国内访问延迟高
- `search_depth="advanced"` 比 basic 慢 2-3 倍
- `max_results=10` 返回更多结果需要更长时间
- `readTimeout=60s` 对于国内访问国外 API 可能不够
- 无自动重试机制

**解决方案**：
- 新增 AI 网页版推荐功能（v1.6），完全绕过 API 调用
- 保留 Tavily 作为可选方案（用户自行配置）

**教训**：对于面向国内用户的应用，依赖国外 API 需要谨慎。提供免费替代方案很重要。

---

### 困难 7：旧版 Gradle 缓存导致构建使用旧代码

**现象**：修改了代码，同步到构建目录，但构建日志显示 `UP-TO-DATE`，新功能未生效。

**原因**：Gradle 的增量编译缓存（`.gradle/` 和 `app/build/`）认为代码未变化。

**解决方案**：
```powershell
# 清理缓存后重新构建
Remove-Item "app\build" -Recurse -Force
Remove-Item ".gradle" -Recurse -Force
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

**教训**：当代码修改后构建结果不符合预期时，先清理缓存再构建。

---

## 六、开发流程规范

### 分支命名

功能分支以 `codex/` 开头，如 `codex/ai-web-recommend`。

### 提交规范

- 只提交本次任务涉及的文件
- 不提交个人照片、服务密钥、签名私钥或构建缓存
- 提交信息格式：`feat: 简短描述` 或 `fix: 简短描述`

### 测试要求

- 每次修改后运行单元测试：`.\gradlew.bat :app:testDebugUnitTest`
- 每次修改后运行 lint：`.\gradlew.bat :app:lintDebug`
- 地图相关修改需额外验证离线显示、缩放、插画和亮暗状态

### APK 发布

- 默认由仓库所有者统一签名发布
- 协作方构建的 APK 使用 `.dev` 后缀包名，用于测试
- 不自行强制推送、删除旧版本或覆盖已发布附件

---

## 七、待开发/可优化方向

### 短期

1. **AI 网页版推荐优化**
   - 自动检测剪贴板内容，识别 AI 回复并自动解析
   - 支持更多 AI 服务（已于 1.7.1 支持 千问 / DeepSeek，现有 5 个）
   - 优化提示词，提高 AI 回复格式稳定性

2. **Tavily 搜索优化**（如果用户仍使用 API 方案）
   - 增加超时时间（connectTimeout 20s, readTimeout 120s）
   - 添加自动重试机制（最多 3 次）
   - 改进 UI 进度提示

3. **WebView 内嵌 AI**
   - 尝试在 app 内打开 AI 网页，自动填入提示词
   - 自动读取 AI 回复内容

### 中期

4. **多 AI 服务支持**
   - 在设置中添加搜索服务选择（Tavily / 博查 / Brave）
   - 添加 HTTP 代理配置

5. **搜索结果缓存**
   - 相同查询缓存结果，减少 API 调用

### 长期

6. **Compose Multiplatform 桌面版**
   - 支持 Windows/macOS 预览

7. **云同步**
   - 用户可选的跨设备同步

---

## 八、快速上手检查清单

接手项目时，按以下顺序检查：

- [ ] 确认 JDK 已安装（`java -version`）
- [ ] 确认 Android SDK 已安装（`$ANDROID_HOME/platforms/android-35`）
- [ ] 将项目复制到纯英文路径
- [ ] 修改 Gradle wrapper 为国内镜像
- [ ] 运行 `.\gradlew.bat :app:compileDebugKotlin` 确认编译通过
- [ ] 运行 `.\gradlew.bat :app:testDebugUnitTest` 确认测试通过
- [ ] 运行 `.\gradlew.bat :app:lintDebug` 确认 lint 通过
- [ ] 阅读 `MainActivity.kt` 了解导航结构
- [ ] 阅读 `Models.kt` 和 `NativeModels.kt` 了解数据模型
- [ ] 确认要修改的功能对应的 Composable 函数在哪个文件

---

## 九、联系方式

- 仓库：https://github.com/z23747424-cpu/travel-journal
- 仓库所有者：z23747424-cpu
- 交接文档：`docs/COLLABORATION.md`
- 构建环境：`docs/构建环境.md`
- 验证记录：`docs/验证记录-1.7.1.md`（最新，含 v1.7.0 链接）
- 地图说明：`docs/地图与联网服务.md`（其中 Tavily 部分已于 1.7.0 作废）

---

## v1.7.0 约定（2026-11-12）

- **没有联网搜索**：`WebTravel.kt`、`NativePlanningAi.kt` 已删除；`AiConfig` 不再有 `searchApiKey`。不要再引入任何搜索 API，也不要恢复「让精灵找下一站」。
- **推荐链路唯一入口**：`AiWebHelper.generatePrompt/copyToClipboard/openAiWeb/parseAiResponse`；结果类型 `OnlineRecommendations` 在 `OnlineRecommendations.kt`。
- **灵感页只改 `NativeInspirationScreen.kt`**（`DiscoverScreens.kt` 只剩 `SearchScreen`）。
- **草稿冲突不能靠禁用控件解决**：统一走 `start(plan)` → `pendingPlan` 对话框（继续草稿 / 放弃并新建），任何新入口都必须复用它。
- **省份自动识别**：`CityAtlas.provinceCodeFor(...)` + `PopularCountyProvinces.kt`；新增热门县级目的地时记得补表并加 `ProvinceLookupTest` 用例。
- **字体**：`res/font/scrapbook_handwriting.ttf` 是 Ma Shan Zheng 子集（GB2312 + 应用字符）。新增大量中文文案后，可用 `fontTools` 核对缺字并重新子集化；勿把正文也换成手写体。

---

## v1.7.1 约定（2026-11-12）

- **AI 网页列表唯一数据源**：`AiWebHelper.services`。增删服务只改这一处，不要在各页面另写一份 chip 列表；改动后请更新 `AiWebTest.servicesOfferQianwenAndDeepSeekButNotTongyi`。
- **服务 id 不落盘**：`aiService` 只在 `rememberSaveable` 中，删除某个 id 不影响老用户（下次进入回落 `DEFAULT_SERVICE = kimi`）。
- **URL 必须是官方 https 官网**，且不要用会跳广告/短链的地址。

---

*文档生成日期：2026-11-12*
*基于 v1.7.1 版本*
