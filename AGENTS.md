# AGENTS.md

面向在本仓库工作的 AI / 开发助手。改代码前先读本文与 `README.md`。

## 项目定位

- **ScheduleTimeline（追程）**：单模块 Android 应用（Kotlin + Jetpack Compose + Room）
- 用途：录入活动行程，用列表时间轴展示；本地存储，无后端
- 包名：Release `com.lamuier.scheduletimeline`；Debug `com.lamuier.scheduletimeline.debug`（Kotlin 源码包名不变；用户可见名见 `app_name`）
- 用户沟通默认用**简体中文**

## 技术基线

| 项 | 约定 |
| --- | --- |
| 语言 | Kotlin 2.3.10（AGP 9 内置 Kotlin，不再应用 `kotlin-android` 插件） |
| UI | Jetpack Compose + Material Design 3 |
| 构建 | AGP 9.2.1 / Gradle 9.4.1 / JDK 17 |
| SDK | minSdk 26 / targetSdk 37 / compileSdk 37（Android 17） |
| ABI | 仅 `arm64-v8a` |
| Release | R8 混淆 + 资源压缩，签名经环境变量注入 |

Gradle Wrapper 与 Maven 依赖优先使用华为云国内镜像；通过 `distributionSha256Sum` 固定官方 Gradle 9.4.1 校验值，官方仓库保留为依赖回退源。

## 架构约定

```
UI (Compose) → ScheduleViewModel (StateFlow) → ScheduleRepository → Room DAO
```

| 层级 | 职责 | 位置 |
|---|---|---|
| Application | 轻量 DI，持有 `ScheduleRepository` | `ScheduleApplication` |
| ViewModel | 日期、事件流、编辑表单 `EditUiState`、导入导出清空、主题模式 | `ScheduleViewModel` |
| Repository | 事务、种子数据、分类联动 | `data/ScheduleRepository` |
| 偏好 | 主题模式（SharedPreferences） | `data/ThemePreferences` |
| 通知 | 通知开关、事件边界调度、Live Updates 兼容 | `NotificationPreferences` / `notification/*` |
| 纯算法 | 时间轴构建、统计、CSV、多团队编解码 | `TimelineBuilder` / `ChartLayout` / `ScheduleExport` / `TeamNames` |
| UI | 主界面拆分，避免单文件过大 | `ui/timeline/*`、`ui/edit/*` |

### 必须遵守

1. **不要**在 Composable 里直接访问 Database/DAO；经 ViewModel。
2. Flow 收集用 `collectAsStateWithLifecycle`，不用裸 `collectAsState`。
3. 表单逻辑进 `EditUiState` + ViewModel；校验错误用枚举/`EditValidationError`，文案走 `strings.xml`。
4. 批量写库用 `@Transaction` / `withTransaction` + `upsertAll`，禁止循环逐条 `save`。
5. 删除团队候选必须同时从所有事件的团队集合中移除该名称。
6. 领域时间统一用 `java.time`（`LocalDate` / `LocalTime`），不要混用 `Calendar`。
7. 用户可见文案放 `res/values/strings.xml`，不要在 Kotlin 里硬编码中文（算法生成的动态提示除外）。
8. 不把业务逻辑塞进 `Activity`；Compose 页面优先用 `Scaffold` + `safeDrawing` + Material 3 Surface。
9. 日程类型仅 `演出` / `特典`；特典必选前特/平特/终特；演出必选一个团队，特典可选多个团队；同日演出与特典按团队名称自动关联。
10. 日程通知必须经全局开关与 Android 13+ `POST_NOTIFICATIONS` 权限；事件保存 / 删除 / 导入 / 清空后同步刷新调度。
11. 「显示未来日程」是独立偏好；关闭总通知时必须一并关闭常驻，空档期展示下一项倒计时而非伪造进行中状态。

## 数据与 Room

- 事件按 `dayKey`（ISO `yyyy-MM-dd`）查询；**种子/导入/保存都必须写入正确 dayKey**，禁止依赖默认值 `"default"`。
- 启动时 `seedIfEmpty` 会把历史 `"default"` 迁移到当天；改种子逻辑时保持此兼容。
- Schema 变更：
  - 提高 `version`，写正式 `Migration`
  - `exportSchema = true`，schema 提交到 `app/schemas/`
  - **禁止**对已发布版本依赖 `fallbackToDestructiveMigration()`（仅允许对极旧版本 `fallbackToDestructiveMigrationFrom(1)`）
- `dayKey` 已建索引；按日查询勿去掉索引。
- 分类与事件目前是字符串关联（非 FK）；删除分类必须清事件字段。
- 团队候选复用 `categories` 表；多团队由 `TeamNames` 在现有 `team` 文本列中编解码，旧单团队数据无需迁移。
- `linkedPerformanceId` 仅保留旧数据兼容；新保存 / 导入恒写 `null`，展示关联只按同日团队名称交集计算。
- 删除事件时继续 `clearLinkedPerformance`，清理旧版本可能遗留的关联 id。

## UI / Compose

- 视觉与交互规范见 [`docs/UI-design.md`](docs/UI-design.md)（色板、字号、圆角、时间轴语义、检查清单）。
- 主屏：`TimelineScreen` + `TimelineTopBar` + `TimelineList` + `ManageDataSheet`；标题在左上，日期导航在其下；仅列表视图；切日带左右滑动动效，非今天显示左下「返回今天」按钮。
- 左右切日须跟随手指位移并在松手后吸附；跨类型重叠组使用双列卡片并显，不退回仅文案冲突提示。
- 图标只用 `material-icons-core`；缺图标用 core 内替代，**不要**重新引入 `material-icons-extended`（体积大）。
- 关键 Composable 尽量带 `@Preview`（空状态、统计条、编辑页、事件卡等）。
- 日期切换、导入/导出/清空入口在主屏菜单；行为变更需同步 `README.md`。
- 主题色优先走 `MaterialTheme.colorScheme`，避免硬编码色值散落在业务页。

## 构建与发版

统一入口：

```powershell
.\build.ps1                 # Debug
.\build.ps1 -Install        # Debug + adb 安装（包名带 .debug，不覆盖 Release）
.\build.ps1 -Release        # 签名打包到 dist/
.\build.ps1 -SetupSigning   # 配置/重绑签名
.\build.ps1 -Help           # 参数说明（也支持 --help）
```

### 调试包与正式版并存（强制）

真机调试时**禁止**用 Debug 包覆盖 / 卸载本机已安装的 Release（`com.lamuier.scheduletimeline`）：签名不同会触发卸载，用户数据被清空。

| 构建 | applicationId | 用途 |
| --- | --- | --- |
| Release | `com.lamuier.scheduletimeline` | 正式版，本机日常使用 |
| Debug | `com.lamuier.scheduletimeline.debug` | 仅调试；`.\build.ps1 -Install` |

- Debug 已配置 `applicationIdSuffix = ".debug"`，与 Release 可同机共存；启动器显示「追程 Debug」。
- 助手 / 开发者装调试包时走 `-Install` 或安装 `app-debug.apk`，**不要**对正式包名 `adb uninstall` / `adb install -r` 覆盖。
- 需要验正式签名或上架包时，另打 `-Release`，再显式安装 `dist/ScheduleTimeline-v*-release.apk`（会与 Debug 并存，仍勿误卸正式数据 unless 用户明确要求重装正式版）。

启动时打印「生效参数」与实际 Gradle 命令行，便于确认开关是否生效。
正式密钥（一次性）：

```powershell
.\build.ps1 -SetupSigning `
  -AdoptKeystore "C:\path\to\release.keystore" `
  -KeyAlias "release" `
  -StorePassword "<pass>"

.\build.ps1 -Release -Online
```

签名环境变量（由 `build.ps1 -Release` 自动注入，一般无需手写）：

```text
ANDROID_RELEASE_STORE_FILE
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

密钥与密码不进仓库；本机签名元数据默认在：

```text
%LOCALAPPDATA%\ScheduleTimeline\signing\
```

`app/build.gradle.kts` 仅在上述环境变量齐备时创建 `signingConfigs.release`；Release 不再硬绑 debug 签名。首次 `-Release` 若尚未配置，`build.ps1` 可自动绑定本机 `debug.keystore`（仅便于本机安装）；上架前必须换正式密钥。

### 版本号（SemVer X.Y.Z）

在 `app/build.gradle.kts`：

```kotlin
versionCode = N      // 整数，上架递增
versionName = "X.Y.Z"
```

- Debug：`versionNameSuffix = "-debug"` → 例如 `1.2.0-debug`
- Release：无后缀 → `1.2.0`
- Release 产物：`dist/ScheduleTimeline-v{versionName}-release.apk`

功能变更必须同步更新 `CHANGELOG.md`，但**不要默认每次小改都升级版本号**。日常增量写入顶部「未归档」；只有用户明确要求「归档/升版本/发版」时才整理为版本节并更新版本号。升版本须全仓一致：`app/build.gradle.kts`、`README.md`、`CHANGELOG.md`（若有根目录 `VERSION` 也一并更新）。

### 版本级别判定（发版前必须执行）

按本批改动中**影响最高**的内容定级，禁止机械地只升级 `Z`：

| 级别 | 适用范围 | 典型示例 |
| --- | --- | --- |
| `X` 主版本 | 不向后兼容的接口、数据语义、权限或部署变更 | 改变字段含义、不兼容 Schema、更换安装包签名要求 |
| `Y` 次版本 | 向后兼容地新增用户可见能力 | 新页面、新导入字段、完整交互功能 |
| `Z` 修订版 | 不新增业务能力的修复与维护 | Bug 修复、样式微调、依赖维护、文档修订 |

- 发版计划须先写明「版本判定：当前 → 目标（依据）」；无法判级时先问用户。
- `versionCode` 必须随新版本递增。
- 用户要求提交发版改动时创建 Git tag（如 `v1.2.0`）；默认不 `push`。

## 安全默认值

- `allowBackup=false`
- `usesCleartextTraffic=false`
- `fullBackupContent=false`
- 数据提取规则排除 SharedPreferences（`data_extraction_rules.xml`）
- Release 禁止 debuggable
- 通知仅使用本地 Room 数据；Android 16 Live Updates 直接使用 API 36 / 36.1（含 promoted ongoing / Status Chip 资格），API 26–35 回退持续通知，不引入后台联网

## 安全与 Git

遵循根目录 `.gitignore`：

- 不提交：`local.properties`、`.gradle/`、`build/`、`dist/`、`*.apk`/`*.aab`
- 不提交：`*.jks`/`*.keystore`、`*.clixml`、`release-signing.json`、`keystore.properties`、`.env` 等
- **要提交**：`app/schemas/`（Room）、源码、`build.ps1`、`lint.xml`、`AGENTS.md`、`README.md`、`CHANGELOG.md`、`docs/`

提交前自检：无密钥、无本机 SDK 路径、无构建产物。

## 测试

- 纯算法优先 JVM 单测：`app/src/test/...`
- 已有：`TimelineBuilder`、`ChartLayout`、`ScheduleExport`、`TeamNames`、`NotificationSchedule`
- 改时间轴/分列/导出格式时**必须**补或更新对应测试
- 默认构建：`.\build.ps1` 会跑 `testDebugUnitTest` + lint（可用 `-SkipChecks` 跳过）

## 文档同步

### CHANGELOG（强制）

每次有实质改动（功能、修复、行为变更、构建/版本相关），**必须**同步写入根目录 `CHANGELOG.md`：

1. **只写进「未归档」**：日常更新追加到 `## 未归档`，不要擅自新建版本节。
2. **分类条目**：用 `### 新增` / `### 变更` / `### 修复` / `### 文档` / `### 构建` 等小标题归类。
3. **不自动归档**：发版、打 tag、推 Releases 时，**不要**由助手自动把未归档挪成 `## X.Y.Z`；等维护者**手动归档**后再形成版本节。
4. **归档后**：未归档区应清空或只留下一批未发布内容；版本节标题格式 `## X.Y.Z - YYYY-MM-DD`。

### README / 本文

下列变更还必须更新 `README.md`（必要时同步本文）：

- 用户可见功能（导入导出、清空、视图、分类）
- 构建方式 / 版本规则 / APK 输出路径
- 项目结构重大调整
- CHANGELOG 维护方式变更

## 常见坑（本仓库实战）

| 问题 | 正确做法 |
|---|---|
| 示例数据有事件但界面空白 | `dayKey` 必须等于查询日的 ISO 日期 |
| 升级数据库数据被清空 | 写 Migration，勿对生产用破坏性迁移 |
| `build.ps1` 找不到 JBR | 安装 Android Studio，或改脚本中的 JBR 路径 |
| Release 签名失败 | 先 `.\build.ps1 -SetupSigning`；密钥不进仓库 |
| Debug 装机覆盖正式版 / 清数据 | 用 `.debug` 包名的 Debug APK（`-Install`），勿对正式 `applicationId` uninstall |
| APK 体积异常变大 | 勿引入 `material-icons-extended` |
| UI 退到后台仍订阅 | `collectAsStateWithLifecycle` |
| 编辑页状态与导航串台 | 用 `prepareEdit` / `EditUiState`，进入编辑页时重置 |

## 改动原则

1. 只改任务需要的代码，不做无关重构。
2. 匹配现有命名与分层；新文件放对包。
3. 不擅自加 Hilt/多模块/云同步等大架构，除非用户明确要求。
4. 不主动 `git commit` / `push`，除非用户要求。
5. 优先可运行、可测试；完成功能后跑相关单测或 `.\build.ps1` 验证。
6. 实质改动必须追加 `CHANGELOG.md`「未归档」；未经明确指示不要归档成版本节。

## 快速检查清单

改完一轮建议确认：

- [ ] `dayKey` / Migration / 事务是否仍正确
- [ ] 文案是否进 `strings.xml`
- [ ] 是否误加 extended icons 或破坏性迁移
- [ ] Manifest 安全默认值是否仍符合基线
- [ ] 单测是否通过（或 `.\build.ps1`）
- [ ] `CHANGELOG.md` 未归档是否已追加本轮条目
- [ ] README / 版本号是否需要更新（不要擅自归档 CHANGELOG 版本节）
- [ ] 无密钥与构建产物进入暂存区
- [ ] 真机调试是否用了 `.debug` 包，未动本机 Release
