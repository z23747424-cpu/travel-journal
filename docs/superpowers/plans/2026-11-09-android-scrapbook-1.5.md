# Android 1.5：将已确认的省份手账与计划做成可安装应用

权威需求是本会话用户已确认的手账原型、`desktop-preview/scrapbook` 和 `docs/旅行计划与地图标记接口.md`。本轮用户指示“完成app制作”，授权原生迁移、构建和 APK 交付；本轮不自动公开发布。

## 全局约束

- Kotlin/Compose 原生应用，包名与旧签名保持，版本 1.5.0 / code 6。地图来自随包真实省界，所有缩放级别无市县几何或地名。市级名称只为记录选填。
- 地图占首页主体，点省翻相册，长按省可记录；已去省柔和填色或照片拼贴；想去手账和用户关联的计划插旗。省份装饰独立保存，不被新笔记重置。
- 采用已批准暖纸、墨绿、自托管手写标题、照片纸边和少量手绘素材。Material3 控件、系统返回、safe area 与字号缩放保留。
- 标记按省自定义 flag/pin/star/heart/image、颜色、16–40dp 大小，图片保持本地文件和 alpha。
- 灵感具有发现/计划/想去三栏。用户模型+Tavily 真实检索推荐 → 生成逐日初稿 → 用户增删/排序/编辑/保存；无配置可手写。无内置目的地名单，海外计划可不关联省份。
- SQLite v1→v2 非破坏迁移；旧 Journal 的坐标/城市字段保留作兼容，用真实边界推导所属省，不猜测未知归属。新增计划、计划草稿、独立省份设计；ZIP v2 备份包含已保存计划与省份设计且接受 v1，排除密钥和未保存草稿，沿用已确认电脑原型的导出范围。
- 优先 WSL 入口，现有脚本在无 Linux SDK/JDK 时调用已安装 Windows SDK/JDK。签名和真机/模拟器证据必须明确，不能把浏览器或 Robolectric 截图说成真机。

## 模块任务与接口

1. 数据与兼容（主智能体）：Journal 末尾加 provinceCode:String? 和 city:String；新增 NativeModels.kt 的 TravelPlan/PlanDay/PlanActivity/TravelSource/ProvinceDesign/WishMarker。Repository 增加 plans/savePlan/deletePlan/loadPlanDraft/savePlanDraft/clearPlanDraft 与 provinceDesigns/saveProvinceDesign；备份事务、引用照片清理和 v1 迁移测试。
2. 原生地图与视觉（分模块实施）：新增 ProvinceHome / ProvinceMapView / ProvinceUi（不替换旧地图类，保留既有测试）。更新 Ui.kt 主题与打包美术。ProvinceHome 参数明确如下：journals, repo, onNew:(Journal)->Unit, onOpen:(Journal)->Unit, onArchive:()->Unit, onDemo:()->Unit, hasDraft:Boolean, onResume:()->Unit, onError:(String)->Unit, designs:List<ProvinceDesign>, plans:List<TravelPlan>, onSaveDesign:(ProvinceDesign)->Unit。负责省份相册、装饰与标记设置。
3. 原生计划与 AI（分模块实施）：新增 NativeInspirationScreen(wishes:List<Wish>, plans:List<TravelPlan>, repo:JournalRepository, onSaveWish:(Wish)->Unit, onDeleteWish:(String)->Unit, onStart:(Wish)->Unit, onSettings:()->Unit, onPlansChanged:()->Unit, onError:(String)->Unit)。内部拥有可恢复计划编辑器及系统返回。复用 WebSearchClient、AiClient.call，不改已有推荐行为。新 NativePlanningAi 负责检索生成和来源一致性验证；对应测试使用注入 transport。
4. 集成（主智能体）：MainActivity 改入口、加载新数据、保留 journal 恢复与编辑逻辑。JournalEditor 改为省份必填、城市/标题选填，保留照片 AI；记录标题空时显示正文摘要。版本/备份提示、来源说明与交付文档。
5. 验证与交付：新持久化、旧数据迁移、计划 AI 流程和原生省份 Canvas 测试，既有完整单测、lint、APK 构建、签名比对。尽力使用模拟器作界面与交互验收；若工具/硬件受限，独立记录限制与实际证据。最后整分支代码及视觉审查，生成 release/旅页-1.5.0.apk 和校验值。

## 预先接口核对

|任务对|共享接口|结论|
|数据/地图|ProvinceDesign、WishMarker、Journal.provinceCode/city|类型先固定；地图不编辑 repository/main|
|数据/计划|TravelPlan 等、repo 方法|计划只写自己的 UI/AI/测试，不改数据模块|
|地图/计划|Ui.kt 主题|主题由地图模块独占；计划复用公开 UI 组件|
|数据/备份|所有新增照片引用|清理和 ZIP 必须覆盖设计封面与自定义图标|
|集成/记录|城市旧字段与新省份字段|不删除历史坐标；编辑时允许明确省份重新归类|

Ruling: 本轮从浏览器原型迁移到原生，不用 WebView 套壳；旧 AI 能力保留。使用分模块实施以隔离地图与计划的变更，最后统一构建，不在并行写入中运行 Gradle 快照。
