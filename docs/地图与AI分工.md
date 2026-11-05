# 地图与 AI 协作边界

用户于 2026-11-05 确认：自己负责地图，同学负责 AI。

## 地图开发

城市地图分支为 `codex/native-city-atlas`，独立工作区 `E:/TravelJournal-city-atlas`。原工作区和旧版本保留。默认分支在合并前仍是 `codex/travel-journal-design`，协作者接手时实时查询远程状态。

地图负责 `CityAtlas.kt`、`NativeTravelMapView.kt`、`MapSurface.kt`、`MapScreens.kt`、`MapStyleControls.kt`、`assets/atlas/`、`scripts/prepare-city-atlas.py` 及对应测试。地图编辑控件也接入 `JournalScreens.kt`。

同学的 AI 工作主要在 `AiClient.kt`、`Search.kt`、`WebTravel.kt`、`DiscoverScreens.kt` 和设置中的 AI 配置流程。地图改造未修改这些 AI 文件。

## 共享接口

`Models.kt`、`JournalRepository.kt`、`Backup.kt`、`MainActivity.kt` 是共享集成点；修改时用单独提交说明字段或回调变化，合并保留双方功能，不用整文件覆盖解决冲突。

Journal 新增字段均有旧数据默认值：

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| mapCityCode | null | 选点时识别的城市代码，地图仍根据实际坐标重新判断城市 |
| mapFillColor | #A9BFAE | 六位十六进制城市填色 |
| mapFillMode | color | color 或 photos，缺照片时回退纯色 |

已有 `latitude`、`longitude`、`visited`、`photos`、`mapSticker`、`mapStickerSize` 保留。地图图片继续来自 `Journal.photos`，未新增独立文件引用；备份的现有图片名重映射仍负责恢复。

同一城市多篇已去手账以最近更新记录的样式为准，拼贴最多采用该城市四张已去记录照片。取消最后一个已去足迹或删除后恢复轮廓。计划、想去、教学示例不自动计作已去。

AI 可以读取原有手账字段；无需处理地图样式。推荐地点不因有城市名字而自动赋坐标、创建已去记录或填城，用户在地图确认位置后才保存。

## 合并和发版

双方分别从最新默认分支创建自己的功能分支并提交 PR。地图分支附测试版 APK 和验证记录，审核合并时特别检查上述四个共享文件。统一由原作者使用同一签名打包覆盖升级；同学本机 debug key 通常不同。

地图矢量边界为本地显示素材，不是 AI 目的地库。AI 的联网推荐不受 391 个绘图行政单元限制。
