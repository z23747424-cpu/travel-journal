---
name: 旅页 · 省份手账桌面原型
description: 仅适用于 desktop-preview/scrapbook 的暖纸旅行日记视觉系统；Android 1.4 历史约定另行保留。
colors:
  paper: '#f7f3ea'
  ink: '#304532'
  green: '#4c6b52'
  muted: '#63705c'
  workspace: '#e9eade'
  photo-paper: '#fffdf6'
  on-green: '#fffdf5'
  map-outline: '#98ac87'
  map-hover: '#3d6244'
  map-label: '#36503a'
  nav-selected: '#dde3cd'
  chip-selected: '#e0e7d1'
  chip-border: '#cdd3be'
  chip-selected-border: '#8eaa78'
  focus: '#526c42'
  placeholder: '#607153'
  helper: '#586a4f'
  error: '#964b35'
  sample-paper: '#f0e5cd'
  sample-ink: '#775d38'
  field-paper: '#fdfbf58c'
  field-border: '#c9cfbc'
  writing-rule: '#b6c3a350'
typography:
  display:
    fontFamily: LvyeHand, 'KaiTi', serif
    fontSize: 44px
    fontWeight: 400
    lineHeight: 1.35
    letterSpacing: 1px
  headline:
    fontFamily: LvyeHand, 'KaiTi', serif
    fontSize: 36px
    fontWeight: 400
    lineHeight: 1.35
    letterSpacing: 1px
  title:
    fontFamily: LvyeHand, serif
    fontSize: 27px
    fontWeight: 400
    lineHeight: normal
  entry-title:
    fontFamily: LvyeHand, serif
    fontSize: 25px
    fontWeight: 400
    lineHeight: 1.5
  body:
    fontFamily: '''Microsoft YaHei'', ''PingFang SC'', sans-serif'
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.7
  journal:
    fontFamily: '''KaiTi'', serif'
    fontSize: 19px
    fontWeight: 400
    lineHeight: 37px
  label:
    fontFamily: '''Microsoft YaHei'', ''PingFang SC'', sans-serif'
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.7
rounded:
  control: 5px
  save: 7px
  form: 8px
  dialog: 13px
  navigation: 16px
  chip: 18px
  search: 22px
  primary: 24px
spacing:
  compact: 8px
  field: 10px
  action-inline: 12px
  dialog: 20px
  content: 22px
  wide-content: 24px
components:
  button-primary:
    backgroundColor: '{colors.green}'
    textColor: '{colors.on-green}'
    rounded: '{rounded.primary}'
    padding: 10px 22px
  button-save:
    backgroundColor: '{colors.green}'
    textColor: '#ffffff'
    rounded: '{rounded.save}'
    padding: 6px 16px
  button-text:
    backgroundColor: transparent
    textColor: '{colors.ink}'
    padding: 6px 12px
  input-search:
    backgroundColor: '#fbf9f0b8'
    textColor: '{colors.ink}'
    rounded: '{rounded.control}'
    padding: 10px 14px
  navigation-item-active:
    backgroundColor: '{colors.nav-selected}'
    textColor: '#385d40'
    rounded: '{rounded.navigation}'
  chip:
    backgroundColor: transparent
    textColor: '{colors.ink}'
    rounded: '{rounded.chip}'
    padding: 5px 13px
  chip-selected:
    backgroundColor: '{colors.chip-selected}'
    textColor: '{colors.ink}'
    rounded: '{rounded.chip}'
    padding: 5px 13px
  photo-frame:
    backgroundColor: '{colors.photo-paper}'
    padding: 8px
  journal-writing:
    textColor: '{colors.ink}'
    typography: '{typography.journal}'
    padding: '0'
  dated-entry:
    textColor: '{colors.ink}'
    padding: 0 0 0 18px
---

> 范围说明：文件顶部机器可读 tokens 与下方新增的 “Design System” 八节仅用于 `desktop-preview/scrapbook`。其前保留的原有视觉约定属于原生 Android／1.4 历史记录；本次未修改或迁移原生应用。

# 旅页视觉约定

用户指定的简洁旅行杂志风是视觉依据。首次制作，没有既有截图或旧界面可继承。

## Tokens

- 纸色 surface: #F7F5EF，正文 ink: #252E2A。
- 主色 primary: #385B50，次要文字 #59655E，暖色标记 #A05B3C。
- 分隔线 #D8DCD3；细线，低圆角，内容区留白 24dp。
- Material3 类型层级：headlineLarge 32sp、headlineMedium 28sp、titleLarge 22sp、bodyLarge 16sp/26sp、labelMedium 12sp。
- 原生 Material3 表单、按钮、对话框和导航；触控最小48dp。

## Surface decisions

首页以原生城市地图为视觉主体，纸色底上，未去城市使用浅色细线或虚线轮廓；已去城市使用柔和预设色、自定义颜色或城市内裁剪的照片拼贴。想去地点以小旗表示，不填充城市。全国视野减少标签、聚合密集足迹，放大后展开。地图控制保持精简，并提供城市搜索和数据来源入口。

长按地图或通过城市列表选择位置，填写地点、去过状态和图块样式后写手账，也可直接保存想去旗。已有地点先展示照片及文字摘要，再进入完整手账。地图插画与城市图块拼贴是独立功能，原有插画选择、尺寸和拖动仍保留。地图由随包边界原生绘制，无需在线地图 SDK、瓦片或 Key。
全部手账与详情保持照片和正文的杂志排版；编辑与设置注重可操作性。旧笔记经用户选点后才显示足迹。空状态示例明确为教学材料，不冒充用户旅行。

四页底部导航，宽屏使用导航栏。详情与编辑支持系统返回。AI 操作有加载、取消、错误和重试；没有配置时链接到设置。无自动AI弹窗和后台调用。

首版固定浅色杂志主题，系统深色下仍维持已设计的纸色主题；其他主题未完成前不提供空按钮。测试字体放大与横屏。导航与地图工具使用 Lucide 1.70.0 细线图标，表单保留 Material 原生控件与图标，精灵不使用未经确认的角色素材。

## 1.4 视觉统一

全国以省界与省名为主，点击后选中省份使用 #EEF2E7 底色、清晰细边，周围仅保留淡色轮廓，不使用全层模糊或遮罩。省内展开城市，地图与页面统一纸色 #F7F5EF。标题采用衬线常规字重，正文保持 Material 类型层级；手账卡片首图采用 1.45:1 比例，地点长文本省略，日期保留。全国、青海、浙江与澳门通过原生 Canvas 离屏检查，整页 Compose 与真实手机仍需设备验收。

---

# Design System: 旅页 · 省份手账桌面原型

## Overview

**Creative North Star: "把旅途贴进一本暖纸日记"**

一本可以慢慢写满的旅行手账：暖象牙纸托住墨绿标题，照片像贴在纸上的记忆，柳叶、远山和胶带只在边角留下轻微痕迹。地图、相册与快记共同采用这一材料语言。

本节与文件顶部 tokens 仅约束 desktop-preview/scrapbook。视觉依据是用户于 2026-11-08 批准的三屏手绘手账稿，实际交付以 HTML、CSS、真实文字和随包省级边界代码实现。Android 1.4 沿用上方历史说明，本次没有迁移。

**Key Characteristics:**
- 暖纸与墨绿构成安静、可读的旅行日记。
- 手写字用于标题，正文、标签和控件保留清晰字体。
- 照片纸框、微倾斜与低对比胶带提供纸本触感。
- 省级地图始终以真实边界和文字标签表达，城市属于选填记录。

当前提取依据：`desktop-preview/scrapbook/style.css`（包括末尾对比度、焦点与状态反馈覆盖）、`app.js`、`data.js`、`map.js`；视觉核对使用 `.tools/scrapbook-qa/desktop-map.png`、`desktop-album.png`、`desktop-editor.png`。这些是浏览器原型截图；本次文档收尾未执行 Android 构建或真机验收。

## Colors

墨绿负责标题与主要行动，浅鼠尾草负责选择反馈，暖纸承载照片与正文。顶部 frontmatter 是此桌面原型的规范值；以下名称说明用途。

### Primary

- **日记墨绿**（`green`）：记一笔、保存设计与保存按钮的实底；搭配奶白按钮文字。
- **深林墨色**（`ink`）：品牌、标题、正文及默认控件文字。

### Secondary

- **鼠尾草选择纸**（`nav-selected`、`chip-selected`）：导航与筛选的选择状态；筛选同时加强边框。
- **地理细绿线**（`map-outline`、`map-hover`、`map-label`）：真实省界、鼠标悬停／键盘焦点和省名；省名带浅纸描边保持辨识。
- **示例暖笺**（`sample-paper`、`sample-ink`）：明确的示例手账标记。

### Neutral

- **暖象牙纸**（`paper`）：设备内底纸、固定行动区和对话框。
- **照片白纸**（`photo-paper`）：照片相框，保留清楚纸边。
- **桌面浅灰绿**（`workspace`）：电脑演示壳外背景。
- **草木灰字**（`muted`）：辅助说明和小字；**校正提示墨**（`placeholder`、`helper`）对应样式表末尾实际覆盖后的占位文字、地图提示与无照片提示。
- **字段半透纸**（`field-paper`）、**字段细边**（`field-border`）、**写字横线**（`writing-rule`）：保留纸感但区分输入区域。
- **可见焦点绿**（`focus`）：原生交互元素的外轮廓；**赭色错误字**（`error`）用于行内保存错误。

**The Readable Ink Rule.** 纸纹和插画不得削弱正文、占位文字、地图提示或焦点轮廓的可辨认性；最终覆盖颜色以当前样式表末尾为准。

地图设计另提供当前代码中的五种柔和填色（`#B8CBB3`、`#D5C5A4`、`#C3CDD0`、`#CFAF9F`、`#D8D5AC`）及用户自定义颜色；这是用户内容样式，不将某个省份固定成品牌色。未去过的省份透明，只显示轮廓；想去用小旗。

## Typography

**Display Font:** 自托管 Ma Shan Zheng，CSS 别名 `LvyeHand`；文件 `desktop-preview/scrapbook/assets/handwriting.woff2`，`font-display: swap`。该文件是现有界面字符子集，来源与 SHA-256 见 `assets/font-source.json`，授权随包 `assets/font-OFL.txt`（SIL OFL 1.1）。新增标题字符须检查字体覆盖；缺字会回退。

**Body Font:** Microsoft YaHei → PingFang SC → sans-serif。日记文字使用 KaiTi，条目段落另有 Noto Serif CJK SC 回退。照片与手写标题表达温度，正文与控制文字负责清楚操作。

### Hierarchy

- **Display:** 省份相册标题（44px）；全国页 **Headline**（36px，窄屏降至34px）。两者共享轻松的手写笔画与（1px）字距。
- **Title:** 快记与对话框标题（27px）；手账条目标题（25px／1.5）。
- **Body:** 基础正文（15px／1.7）；相册段落（16px／1.8），详情日记（18px／1.9）。
- **Journal:** 快记正文（19px／37px），行距与纸上横线对齐。
- **Label:** 常规表单标签（13px），辅助标记按实际组件使用（10–12px）；地图省名（10.5px）随地图缩放维持屏幕字号。

**The Handwritten Headings Rule.** 手写展示字体服务于品牌和标题；表单、日期、导航与辅助说明继续使用可读的正文栈。

## Layout

电脑工作区为说明栏与可切换宽度的设备壳，居中留白。默认设备壳宽（430px），宽屏预览为 `min(750px, 70vw)`；设备高度为 `min(950px, calc(100dvh - 64px))`，最小高度（740px）。它是浏览器演示框架，不代表已运行的 Android 设备。

页面采用纵向 flex：顶栏、可滚动内容和固定行动区分工明确。地图占中间剩余空间，最近回忆和“记一笔”留在底部；相册为大封面、日期时间线与底部行动；快记为照片、横线写字区和省份／选填城市／日期。主内容侧边留白主要为（22–26px）；组件内紧凑间距采用（8–12px）。

- 在（850px）及以下缩小说明栏和演示壳；宽屏模式隐藏说明栏。
- 在（600px）及以下隐藏桌面说明栏，设备框占满视口并移除壳边、圆角与阴影；照片最大高（300px），地图最小高（220px）。这仍是响应式网页。
- 桌面视口高度（760px）及以下时，演示壳使用（850px）高度，页面可向下滚动，说明栏粘在顶部。
- Toast 在桌面底部（25px）；窄屏覆盖调整为底部（132px），避免压住底部行动区。

## Elevation & Depth

深度来自暖纸、浅透明层、照片纸边与低幅阴影。纸纹 `assets/paper.png` 平铺，背景尺寸（510px）；透明装饰图集 `assets/decor.png` 承载柳叶、远山、胶带与银杏叶，均属于边角材料。装饰使用 `pointer-events: none`，不承担文字或交互。

### Shadow Vocabulary

- **轻纸层**：`0 6px 15px #655a3b14`，用于回忆条与 Toast 等共享纸层。
- **照片纸边**：`0 5px 11px #7e725827`，搭配微倾斜照片相框。
- **演示设备壳**：`0 18px 54px #37423225`，只用于电脑展示框。
- **模态纸页**：`0 14px 45px #303d302f`，背景遮罩 `#2434254f` 仅限模态对话框。

**The Paper Before Float Rule.** 用纸色分层、照片边缘和低对比阴影说明层次；装饰不遮挡内容，不接收指针事件。

仅在用户未要求减弱动态时，页面进入使用（220ms）`ease-out`，透明度（0.7 → 1）与纵向位移（5px → 0）。当前主按钮未定义额外悬停动效，不应把大幅弹跳或连续装饰动画写成已实现行为。

## Shapes

主行动是柔和胶囊（24px），筛选是小胶囊（18px），输入框保持低圆角（5px），元信息组（8px），对话框（13px）。照片纸框保留直角、纸边与轻微倾斜（−0.6°）；胶带有透明和不规则边缘，不能替代按钮形状或文字。

图标采用代码中的内联 SVG 细线，常规尺寸（22px）、描边（1.65px）；控件通常至少（48px）高，地图工具（36px）、快记保存（40px）和缩略图移除（29px）按当前 CSS 保留。这里记录实际尺寸，不宣称所有控件已经满足统一触控尺寸。

## Components

### Buttons

“记一笔”使用墨绿胶囊与奶白字，内边距见 frontmatter；相册固定行动区占满可用宽，地图页主行动占内容宽的（78%）。快记顶栏保存是较小的实底圆角按钮。次要行动用文字和细线图标。全局 `:focus-visible` 为（2px）实线外轮廓、偏移（3px）；保存处理中禁用按钮，透明度（0.55）并显示等待指针。

### Chips

筛选“全部／回忆／想去”及编辑状态沿用细边小胶囊；选中时同时改变底色、边色和 `aria-pressed`。不以颜色作为唯一语义。

### Cards / Containers

回忆条使用横排照片与文字、轻纸阴影。相册封面使用（8px）纸边和（1.52:1）照片；编辑照片（1.4:1），日记缩略图按当前页面布局裁切。点开照片使用原图查看容器，不把显示裁切写回上传文件。时间线使用细竖线、圆点、浅绿日期纸条与手写标题。

### Inputs / Fields

快记写字区是可调整高度的真实 textarea，背景横线周期（37px）。元信息以整组浅纸细边承载三行：省份、城市（选填）、日期；更多内容折叠收起标题、标签和“去过／想去”。搜索是低圆角浅纸单行字段。占位文字使用末尾覆盖后的 `placeholder` 色；错误在写字区附近用 `role="alert"` 显示，并聚焦需补充的字段。

### Navigation

主导航为“地图／搜索／灵感／设置”，使用细线图标与小字号标签，活动项带浅绿圆角底与 `aria-current`。相册、详情与快记隐藏主导航，用“全国”或上一级相册返回，避免层级混乱。桌面侧栏的“手机／宽屏”只切换网页预览尺寸。

### Province Map

`atlas.json` 中的省级 GeoJSON 由 SVG 路径、标签与裁剪渲染；省名、搜索结果和工具均为真实文字与控件。所有缩放层级都使用省界，城市是手账中的选填文本。省名通过屏幕尺寸保持和疏密控制避免完全随地图缩小。可用鼠标滚轮／指针平移、缩放按钮和键盘选择；已去填色或省界内照片拼贴，想去仅插旗。省份设计独立于记录保存。

### Materials & Provenance

- 批准稿：`design-previews/scrapbook-v01/旅页-手账手绘三屏-v02.png`，由内置 imagegen 生成，仅为视觉依据，来源与批准范围见同名 `.png.json`。
- 纸纹、透明装饰、湖亭和老街样例均由内置 `image_gen__imagegen` 按批准稿于 2026-11-08 派生，精确提示词和生成源文件路径记在 `desktop-preview/scrapbook/assets/image-prompts.json`。`paper.png` 为不透明材料；`decor.png` 为带 alpha 装饰图集。
- `lake.jpg.png` 与 `street.jpg.png` 是 AI 生成的示例照片，未宣称为用户实拍。侧栏、设置、相册和示例条目标记其示例性质；用户导入内容按自己的文字和照片显示。
- `.impeccable/design.json` 使用 schemaVersion 2，仅记录 frontmatter 无法表达的元信息、色阶预览、阴影、动态、断点与可渲染组件片段。色阶是展示面板派生值，不是新增应用颜色；照片片段用明确占位表达纸框，不伪装成额外照片资产。

### Differences from the Approved Board

- 批准稿的静态手绘省界和十个主要标签，已替换为随包真实省级路径、可选择省名、南海插图和缩放工具；地图轮廓、比例和标签排布以数据为准。
- 参考中的大幅水彩笔触、邮戳、装饰性下划线和满幅山景在当前实现中更克制，未逐件完整复刻；纸纹、柳叶、胶带和纸框保留其主要材料感。
- 当前相册加入真实记录数量与筛选；快记加入照片缩略图、移除、状态、草稿和有效性反馈。控件由代码布局，字形和细节不与生图像素一致。
- 编辑截图使用老街样例，批准稿编辑页是日落枫叶照片；本次仅交付湖亭与老街两张明确标记的 AI 样例。
- 浏览器版本额外提供电脑说明栏、尺寸切换和演示壳。截图核对不构成 Android 1.4 迁移、真实手机、服务调用或发布验证。

## Do's and Don'ts

### Do:
- Do 保留暖纸、墨绿、细线、纸框照片与克制的角落装饰。
- Do 使用真实省级边界、可选择的省名与可聚焦控件，所有缩放层级保持省级地图。
- Do 将城市和标题保持选填；快记只要求省份、有效日期以及照片或正文。
- Do 显式标注 AI 示例照片和示例手账，保持用户照片与文字优先。
- Do 保存未完成草稿；保持省份装饰与单篇手账独立。
- Do 区分桌面浏览器原型、批准美术稿与 Android 1.4；后续原生迁移单独验收。

### Don't:
- Don't 把批准生图当成整张界面、文字层、地图边界或真机截图。
- Don't 在该桌面省级地图上自动展开市县边界，或把城市改成保存必填项。
- Don't 把手写展示字铺满全部控件与正文，或用低对比装饰遮住输入内容。
- Don't 将生成照片表述为用户实拍、真实旅行证据或已发生的行程。
- Don't 将浏览器截图或现有 QA 记录扩大成 Android 构建、联网 AI、实机或发布验证。
