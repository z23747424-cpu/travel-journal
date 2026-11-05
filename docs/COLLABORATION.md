# 旅页：同学协作与 AI 交接指南

核对日期：2026-11-05。本文件供仓库所有者、协作同学和同学的开发 AI 使用。它说明现有项目与默认协作流程；后续明确分配的任务优先于这里的建议。

## 1. 先由两位同学完成 GitHub 接入

仓库：https://github.com/z23747424-cpu/travel-journal

### 仓库所有者操作

1. 获取同学自己的 GitHub 用户名。
2. 在仓库打开 `Settings → Collaborators → Add people`，选择对应用户并发送邀请。
3. 等对方接受后，确认协作者列表中已生效。

### 被邀请同学操作：在哪里接受

1. 使用被邀请的那个账号登录 GitHub。
2. 打开 GitHub 发到该账号邮箱的邀请邮件，点击 `View invitation`，再点击 `Accept invitation`。
3. 也可以登录后打开本仓库的[邀请入口](https://github.com/z23747424-cpu/travel-journal/invitations)，查看是否有待接受邀请。
4. 如果没有邀请提示，双方核对用户名、当前登录账号、邀请是否已发送或已过期。公开仓库可以被读取，写入权限需要另外确认。

本指南没有替任何人发送邀请，也没有确认某位同学已经接受。需在自己的账号下核对实际状态。参考：[GitHub 邀请流程](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository)、[邀请接口与网页入口格式](https://docs.github.com/en/rest/collaborators/invitations)。

## 2. 交给 AI 前，同学只需要准备这些

- 自己的 GitHub 账号，且已接受邀请；Git/GitHub CLI 的浏览器登录由自己完成。
- 一个新的项目目录，或已有且明确属于此项目的工作目录。
- 把文末的“直接发给 AI 的提示词”交给开发 AI。首次任务是接手与复现基线，具体功能随后单独提出。

建议分工：同学与 AI 在功能分支开发并提交 PR，仓库所有者审核合并和统一签名发版。对方如需独立发 APK，还需先安排统一签名方案。当前没有 GitHub Actions 自动打包，推送代码本身不会生成新安装包。

## 3. AI 首次接手：确认身份、权限和分支

以下为安装了 GitHub CLI 的命令示例；连接器工具足够时也可使用对应 GitHub 工具。缺少 CLI 时从[官方安装说明](https://cli.github.com/)安装，登录采用浏览器授权，不在聊天中索要密码或 Token。

```bash
gh auth status
gh api user --jq .login
gh repo view z23747424-cpu/travel-journal --json url,defaultBranchRef,viewerPermission
```

- 登录身份应当是协作同学自己的账号；仓库所有者仍为 `z23747424-cpu`。
- 直接推送需有 `WRITE` 或更高权限；显示 `READ` 时先核对邀请。读取公开仓库仅证明可读。
- 默认分支在本次核对时为 **`codex/travel-journal-design`**，每次接手都实时读取，不假定是 `main`。
- 暂无写权限也能在公开仓库基础上 Fork 并提交 PR；采用该路线时明确 upstream 为原仓库，origin 为同学自己的 Fork。

新目录首次拉取示例：

```bash
git clone https://github.com/z23747424-cpu/travel-journal.git
cd travel-journal
git status --short --branch
git remote -v
git log -5 --oneline
```

已有目录先检查未提交内容，保留已有工作；不要为了接手而删除目录、重建仓库或重置提交历史。提交身份采用同学自己的 Git 配置，不冒用仓库所有者身份。

## 4. 必须先读的文件

按顺序阅读：

1. `README.md`：使用流程和当前交付版本。
2. `PRODUCT.md`、`DESIGN.md`：产品约束与视觉方向。
3. `app/build.gradle.kts`：真实包名、版本和依赖。
4. `docs/构建环境.md`：固定工具链及中文路径处理。
5. `docs/验证记录-1.2.md`：最新已记录检查与证据边界。
6. `docs/地图与联网服务.md`：地图来源、资源路由、联网推荐与测试方式。
7. `docs/superpowers/specs/` 中与当前任务有关的设计，以及工作区实际存在的 `AGENTS.md`。

当前版本快照是 1.2.0，`versionCode=3`，包名 **`cn.edu.traveljournal`**。当前默认分支可能比 1.2 标签多出文档提交，版本与代码以实时读取为准。已有 APK/源代码附件在 Releases，不是克隆仓库后就自动拥有的本地文件。

## 5. 不要在接手时改变的产品方向

- Android 应用，以 Kotlin/Compose 和本机 SQLite JSON 记录为基础。
- 首页直接显示完整中国省级底图，默认离线可显示、缩放和平移。可选街道图层联网失败时，完整底图仍须保留。
- 地点由使用者自己设计，可导入透明 PNG 插画或照片、调大小、拖位置，再点击查看笔记。同位置多篇可展开列表。
- 标记默认小尺寸，全国视野限制插画尺寸并收起文字；实际触控区域保留至少 48。
- **去过的区域提亮、未去过的区域暗色**。以有坐标的去过笔记点亮对应省级区域；未去过的计划和教学示例不默认算实际足迹。
- 旧笔记、图片、坐标、去过状态、插画和大小需要保留；新增数据字段提供兼容默认值，备份恢复保持引用一致。
- 搜索界面独立，杂志风的照片和文字排版保留。
- AI 服务由使用者自行配置；旅行推荐先实际联网搜索网页再由 AI 整理，没有运行时内置目的地推荐库。
- 不把历史 `experiments/` 固定目录基线当成当前联网推荐准确率。
- 中国边界、Leaflet 和在线街道图层保留已有来源与许可说明。

## 6. 代码定位

| 任务 | 主要文件 |
| --- | --- |
| 导航、草稿、保存、页面状态 | `MainActivity.kt` |
| 记录、位置、插画、去过状态 | `Models.kt` |
| SQLite、照片、拖动保存、加密配置 | `JournalRepository.kt` |
| JSON、ZIP、导入冲突和图片名重映射 | `Backup.kt` |
| 地图首页、地点菜单、选点弹窗 | `MapScreens.kt` |
| WebView 生命周期、最小 JS 桥 | `MapSurface.kt` |
| Android 本地 HTTPS 资源路由 | `MapResources.kt` |
| 地图载荷、坐标校验 | `MapLogic.kt` |
| 本机透明图片缩略副本 | `MapImage.kt` |
| 地图几何、缩放、标记、亮暗 | `app/src/main/assets/map/` 中的 JS/CSS/HTML |
| 手账详情、编辑、图片和插画选择 | `JournalScreens.kt` |
| 原生主题和常用排版 | `Ui.kt` |
| 关键词搜索、AI 识图与语义检索 | `Search.kt`、`AiClient.kt` |
| 真实网页搜索与来源约束 | `WebTravel.kt` |
| 灵感、搜索、想去清单 | `DiscoverScreens.kt` |
| 模型/搜索配置、导入导出 | `SettingsScreen.kt` |

上表未写全路径的 Kotlin 文件均位于 `app/src/main/java/cn/edu/traveljournal/`。

## 7. 在同学电脑建立构建环境

先识别实际操作系统和已安装工具。Windows 上开发优先 WSL2/Ubuntu；本机 SDK 只安装在 Windows 时，可使用项目的 Windows 构建入口。原作者机器的 `E:\Android\Sdk` 等路径只是实例，不是同学电脑应照抄的依赖。

固定工具链：

- JDK 17 或兼容的 JDK 21；检查实际版本，不假设 Android Studio 自带 JBR 一定适用。
- Android SDK Platform 35、Build Tools 35.0.0、Platform Tools。
- Gradle Wrapper 8.9、AGP 8.7.3、Kotlin 2.0.21；直接用仓库 Wrapper，接手阶段不批量升级依赖。
- 手机最低 API 26；编译和目标 API 35。

Linux/完整 WSL 工具链，从项目根目录运行：

```bash
bash scripts/build.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

PowerShell/Windows SDK 路线，在根据真实安装位置设置 `JAVA_HOME`、`ANDROID_HOME` 后运行：

```powershell
.\scripts\build-windows.ps1 -Tasks @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebugAndroidTest')
```

Windows 中文路径的 Java 测试类加载曾出现问题，项目脚本会用逐文件核对的英文临时副本构建。Linux SDK 与 Windows SDK 不混用；Linux 不能直接把 Windows 的 Build Tools 当本机工具使用。

首次下载依赖可能较慢；保存实际错误、构建日志和版本。不要通过提高最低 Android 版本、关闭 lint、跳过测试或删掉照片校验来掩盖环境问题。

## 8. 基线与地图检查

1.2 在原作者机器上记录：53 项单元测试通过，lint 0 错误、7 项依赖更新提示。**这是历史基线，同学电脑需要重新运行。** 同学应报告自己的实际结果。

地图相关修改额外运行：

1. 用本地 HTTP 服务提供 `app/src/main/assets/map/`。
2. 按 `docs/地图与联网服务.md` 安装固定 Playwright 测试依赖和 Chromium。
3. 执行 `scripts/test-map-offline.cjs` 与 `scripts/test-map.cjs`；请求拦截保持有效，测试使用本地数据与夹具。

测试重点：

- 断网首页仍绘制完整中国底图；缩放与平移正常。
- 插入个人 PNG，透明背景保留，标记尺寸适当。
- 去过提亮、未去过暗色；切换状态和删除足迹后颜色正确。
- 点击已有标记打开笔记，同地多篇列表可用。
- 拖动后重启仍保留位置；备份导入后图片、状态和引用正确。
- 旧记录可读写；搜索和想去清单保持原有功能。

有专用测试手机或模拟器时再运行设备测试：

```bash
bash scripts/build.sh :app:connectedDebugAndroidTest
```

设备端流程会创建和清理测试数据，使用专用测试安装。个人正式笔记先备份。只有实际运行后才能报告真机、相册、触屏或 WebView 整机验收；浏览器组件截图单独注明范围。

## 9. 每次更新的分支和 PR 流程

收到具体功能后，先明确验收结果，再从最新默认分支新建 `codex/` 开头的功能分支。下面分支名仅为例子，AI 按任务取唯一名称：

```bash
git fetch origin
base_branch=$(gh repo view z23747424-cpu/travel-journal --json defaultBranchRef --jq '.defaultBranchRef.name')
git switch -c codex/classmate-map-update "origin/$base_branch"
```

实现、运行相关检查并查看差异后，只提交本次任务文件。仓库是公开的，签名私钥、服务密钥、个人照片和生成缓存不进入提交。

```bash
git status --short
git diff --check
git add path/to/changed-file
git commit -m "feat: describe the actual change"
git push -u origin codex/classmate-map-update
```

然后在网页创建 PR，或使用 `gh pr create`，明确 base 为查到的默认分支。描述包含：问题与结果、修改范围、实际测试、截图范围、数据兼容情况、尚未验证事项。命令行提交多段正文时使用正文文件。

默认由仓库所有者审核合并。协作者有写权限时可能能直接推默认分支；本团队默认工作流选择分支和 PR，未经本次任务授权不自行合并、强制推送、删除历史标签或覆盖已发布附件。若并行开发导致冲突，先同步、保留双方工作并检查最终结果。

只有接入检查而没有代码或文档变更时，不创建空 PR。

## 10. APK 发布与统一签名

源码协作、APK 构建、签名和 Release 发布是几个步骤。当前仓库没有自动打包流水线，也没有 app 自动升级模块。

推荐当前分工：

1. 同学提交功能 PR 和测试证据。
2. 所有者合并后，从同一最终提交重新构建。
3. 发布者保留包名 `cn.edu.traveljournal`，递增 `versionCode` 并确定新的 `versionName`。当前是 3 / 1.2.0，后续以发布时实际值为准。
4. 使用一致的签名生成更新 APK，核对包名、版本、签名和 SHA-256。
5. 新建对应 Git 标签和 GitHub Release，上传新 APK、对应源码包和校验文件。旧版本和附件保留。
6. 手机用户下载新 APK 覆盖更新；源码提交不会自动更新手机。

当前公开安装包使用原作者电脑的 Android Debug 测试签名。证书 SHA-256：

```text
52c322178761237f5721c15464f6c142323f4bc236a625e8b622a0b491a75398
```

私钥未放在仓库。同学电脑默认生成的 debug key 通常不同；同学自己的测试包可以在专用测试安装中使用，原用户的覆盖更新由所有者统一签名最方便。后续如需同学独立发版或 GitHub Actions，另行安排受控签名配置，签名私钥不进入公开源码。参考：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)。

## 11. 直接发给 AI 的提示词

```text
你现在接手“旅页”Android 旅行手账项目，帮助我作为协作同学参与开发。

仓库：https://github.com/z23747424-cpu/travel-journal
交接文档：https://raw.githubusercontent.com/z23747424-cpu/travel-journal/codex/travel-journal-design/docs/COLLABORATION.md

请先完整读取交接文档，再读取仓库实际存在的 AGENTS.md、README.md、PRODUCT.md、DESIGN.md、app/build.gradle.kts、docs/构建环境.md、docs/验证记录-1.2.md 和 docs/地图与联网服务.md。所有当前状态以实际源码和远程元数据为准。

首次只完成“接手与基线验证”：
1. 确认当前 GitHub 登录为我自己的账号，核对我对该仓库的写权限。
2. 实时读取默认分支；当前已知为 codex/travel-journal-design，不假设 main。
3. 检查本地已有文件和 Git 状态，保留已有工作；在合适的新目录克隆，或安全复用正确仓库。
4. 自动识别操作系统、JDK 和 SDK。Windows 开发优先 WSL；只有 Windows SDK 时用项目 build-windows.ps1。不要照抄原作者机器路径。
5. 使用固定 Gradle Wrapper 运行 Android 编译、单元测试、lint 和设备测试代码编译，记录你实际得到的结果。原作者的 53 项通过是历史基线，不是你已执行的结果。
6. 汇报权限、分支、当前提交、环境、实际构建/测试和阻碍。缺少邀请或登录时明确指出由我完成的那一步，不索要账号密码或把令牌写进源码。

我随后会给具体功能任务。在此之前不擅自改产品功能、不批量升级依赖、不创建空 PR、不发布新版本。

后续开发时：
- 从最新默认分支创建 codex/ 功能分支，完成明确要求和必要回归检查，推送功能分支并创建 PR，由所有者审核。
- 保持完整中国底图离线可见、可缩放，保留个人小插画与拖动、去过亮色/未去过暗色、旧数据和备份兼容。
- 推荐真实联网搜索，不恢复内置目的地限制，不把计划或教学示例说成用户实际去过。
- 修改地图时验证实际几何显示与离线状态，不能只测试按钮或用纯色夹具冒充地图显示。
- 区分单元测试、浏览器组件、真机与真实服务验证；没有执行的检查明确标为未验证。
- 默认保持包名；版本号只在发布任务中统一递增。当前 APK 使用原作者的签名，同学本机包不能直接冒充兼容的覆盖更新包。
- 交付时给出改动、实际测试、PR 地址和剩余限制；保留旧版本，不覆盖已发布附件。
```

以上提示词用于把 AI 带到正确项目。具体下一项功能由同学随后说明，不能让 AI 根据“继续优化”自行扩展范围。
