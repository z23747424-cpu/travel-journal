# Android 1.5 设计核对

2026-11-09 · 对照已批准的手账手绘三屏 v02 与当前 Android 源码。结论：原生版本延续暖纸手账世界，采用 Material 3 的移动端排版、控件和导航适配；可见范围 finish 核对通过，设备 UI 尚未验收。

## 设计延续

| 项目 | 当前原生实现与依据 |
| --- | --- |
| 色彩 | `Ui.kt`：纸底 `#f7f3ea`、主绿 `#4c6b52`、墨色 `#304532`；显式暖色 surface-container 系列承接卡片，未照搬 Material 默认冷色面板。 |
| 文字 | `scrapbook_handwriting` 字体资源承接手写标题；`Ui.kt` 的 headlineLarge/headlineMedium/titleLarge 为 32/28/24sp，正文和控件为系统字体，bodyLarge 为 16sp、26sp 行高。字体子集为有限覆盖，不宣称覆盖所有汉字。 |
| 材质 | `ScrapbookPaper` 和省份主页使用独立纸纹（透明度 0.38）；省份装饰取自装饰位图。照片、装饰与可交互省界各自独立。 |
| 地图 | `ProvinceMapView.kt` 原生路径绘制、命中与省内照片裁切，维持省份层级；照片是位图素材，地图不是整张不可交互的预览图片。 |
| 布局 | `MainActivity.kt` 以 720dp 为侧栏断点，较窄屏幕采用底部导航；编辑状态隐藏主导航。计划编辑使用可滚动原生表单、safeDrawingPadding 和 imePadding。 |
| 灵感与计划 | `NativeInspirationScreen.kt` / `NativePlanEditor.kt` 延续标题、纸底与暖色卡片，使用原生输入、确认弹窗、逐日安排与来源链接；计划标记不冒充“已去”。 |

桌面仍由根 `DESIGN.md` 与 `.impeccable/design.json` 约束；历史 Android 1.4 记录保持原状。本次仅新增 Android surface 说明，不把原生适配值写成桌面规范，也不修正既有工具元数据漂移。

## 可复查证据

- 原生渲染图目录：`app/build/reports/native-ui-15/`。文件为 `phone-map.png`、`phone-after-selection.png`、`phone-editor.png`、`phone-inspiration.png`、`phone-plan-editor.png`、`phone-plans.png`、`phone-font130.png`、`tablet-map.png`。
- 图像来自实际 Compose UI 的 Robolectric 软件 Canvas 绘制。独立 finish 检查逐一打开 8 张图并核对源码，在该可见范围内未提出实质修复；不等同于设备或模拟器操作验收。
- `.tools/android-15-artifacts/verification-summary.json` 记录 113 项测试，失败、错误、跳过均为 0，Release 构建与 lint 通过；`.tools/android-15-release-build.log` 结尾为 `BUILD SUCCESSFUL`。
- 交付 APK：`release/旅页-1.5.0.apk`，versionCode 6，14,867,147 字节。SHA-256：`772b79fd68e5201f17f3b789dc615d039a1d68a69fc48eb576e495bce945322c`；签名沿用 1.4。

## 验收边界

当前证据支持源码、软件渲染可见状态、自动化测试和 Release/lint 结果；未验证真机安装升级、触摸手势、实际输入法遮挡、系统字体回退、硬件 Canvas 与真实联网 AI 服务。验证摘要中 `deviceTested`、`realAiProviderTested` 均为 `false`。后续设备结果应另行记录，不能由截图或构建成功推定。
