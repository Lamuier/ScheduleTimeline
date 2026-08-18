# 更新日志

本文件记录 ScheduleTimeline 的用户可见与工程向变更。

## 维护规则

- 日常改动**只追加到「未归档」**，不要直接写版本号小节。
- 发版时由维护者**手动归档**：把未归档条目挪到 `## X.Y.Z - YYYY-MM-DD`，再清空或重置未归档区。
- 已归档版本的条目必须放在对应版本小节并按类别整理；「未归档」只保留最近一次发版后的改动。
- AI / 助手不得在未接到明确「归档」指示时创建版本节。

条目建议分类：`新增` / `变更` / `修复` / `文档` / `构建`。

---

## 未归档

## 1.11.2 - 2026-08-18（灰度测试，验证后转正）

### 修复
- 常驻通知锁屏隐私补齐：`postLiveUpdate` 由 `VISIBILITY_PUBLIC` 改为 `VISIBILITY_PRIVATE` 并提供 `setPublicVersion` 脱敏公开版（仅应用名 +「日程进行中 / 日程即将开始」状态概要），锁屏不再暴露团队名、时间与下一项行程；解锁表面（含灵动岛 / Live Updates）展示不受影响

### 文档
- 新增 `PRIVACY.md` 隐私政策（纯本地存储、权限用途、锁屏脱敏、导出行为、备份排除），可供应用市场审核引用
- 新增 `THIRD_PARTY_NOTICES.md` 第三方组件许可证声明（运行时依赖均 Apache 2.0；测试 / 构建期依赖单列），作为 packaging 去重 LICENSE/NOTICE 文本后的集中声明
- README 文档表补充以上两份文档入口

### 构建
- 开源协作可用性：补齐 Unix `gradlew` 脚本（LF 入库、git 可执行位 `100755`），Linux/macOS 贡献者克隆后可直接 `./gradlew` 构建；`gradlew.bat` 与 `gradle-wrapper.jar` 经 `gradle wrapper` 任务同步再生成（9.4.1 官方模板），华为云镜像 `distributionUrl` 与 `distributionSha256Sum` 校验保持不变
- APK 体积优化：1.68 MB → 1.33 MB（约 -21%）
  - `androidResources.localeFilters` 仅保留中英文资源（与 `locales_config.xml` 声明一致），`resources.arsc` 由 335 KB 降至 28 KB
  - 删除 5 密度 × 2 的 legacy PNG 启动图标；minSdk 26 下 `mipmap-anydpi-v26` 自适应图标在全部设备恒生效，PNG 属死重量
  - packaging 排除依赖库嵌套的重复 `META-INF/**/LICENSE.txt` / `NOTICE.txt`（15 KB → 0.7 KB，仅保留 .version 与 services）
- `build.ps1 -Release` 打包成功后自动清理 `dist/` 中旧版本产物，仅保留本次版本文件（apk / mapping / sha256 / build.json）；构建失败不动旧版本

## 1.11.1 - 2026-08-18

### 变更
- 数据提取规则纵深加固：`data_extraction_rules.xml` 云备份与设备迁移均追加排除 `database` domain，Room 数据库（行程数据）不参与 Android 12+ 备份/迁移

### 修复
- CSV 导出/导入支持 RFC 4180 引号转义：字段含逗号 / 引号 / 换行时导出端自动加引号（内部引号翻倍），导入端改为引号感知解析，杜绝含逗号字段导致的列错位与数据丢失；历史无引号导出仍可正常导入（字段中部半角引号按字面保留）
- 通知锁屏隐私加固：常驻通知与关键时间点提醒由 `VISIBILITY_PUBLIC` 改为 `VISIBILITY_PRIVATE`，锁屏不再直接展示行程明细（团队、地点、时间），仅显示应用名

### 构建
- 收窄 Release 混淆保留规则：由整个 `data` 包收窄为仅 Room 实体（`ScheduleEvent` / `Category`）；`TimelineBuilder` / `ScheduleExport` / `ScheduleRepository` / `TeamNames` 等纯 Kotlin 类恢复 R8 混淆与内联优化（已验证 `ScheduleDatabase_Impl` 反射关键类保持原名），APK 1.71 MB → 1.68 MB

## 1.11.0 - 2026-08-17

### 新增
- 桌面快捷方式（App Shortcuts）：长按图标提供「新增日程」（固定为今天新增并直达编辑页）、「今日日程」（一键回到今天）、「最近日程」（跳到今天含起未来最近有日程的日期）、「批量导入」（直达管理面板 CSV 导入步骤）；`MainActivity` 改为 `singleTask` 以保证热启动走 `onNewIntent` 正确路由

### 变更
- 事件详情页备注与冲突提示分离展示：用户备注改为中性样式（Info 图标 + `surfaceVariant` 底），感叹号警告样式仅用于真实时间重叠的自动提示；即使备注文本提及冲突也不再抑制警告条

### 修复
- Shortcut 点击无响应：静态 `shortcuts.xml` 的 `<intent>` 省略 `targetPackage` 在部分启动器上解析失败，且无法同时兼容 Debug（`.debug` 后缀）与 Release 包名；改为 `AppShortcuts` 进程启动时动态注册 Dynamic Shortcuts（显式 intent），并在旋转重建时不重放已消费的 shortcut intent

## 1.10.0 - 2026-08-16

### 新增
- 数据管理新增「清空当日日程」：按 `dayKey` 事务删除当前查看日期的全部事件（含旧版本遗留关联 id 清理），其他日期与团队候选不受影响；确认页显示日期与待删事件数；清空后同步刷新通知调度

## 1.9.0 - 2026-08-16

### 新增
- 常驻通知启用 Android 17（API 37）Live Updates 语义颜色：进行中 = 蓝色（INFO）、空档等待下一项 = 绿色（SAFE），作用于进度段与正文语义注解，仅在被提升为 Status Chip / 岛表面时由系统着色；API 36 及以下行为不变

### 构建
- 适配 Android 17（API 37）：`compileSdk` / `targetSdk` 升至 37，`buildToolsVersion` 升至 37.0.0（`compileSdk` 改用 AGP 9 新 DSL `release(37)`）
- 工具链随之升级：AGP 8.13.2 → 9.2.1、Gradle 8.13 → 9.4.1（华为云镜像 + 官方 SHA-256 校验）、Kotlin 2.0.21 → 2.3.10、KSP 2.3.10、Room 2.6.1 → 2.8.4；AGP 9 启用内置 Kotlin，移除 `kotlin-android` 插件并把 `kotlinOptions` 迁移为 `kotlin { compilerOptions }` DSL；`fallbackToDestructiveMigrationFrom` 改用 Room 2.8 新签名（行为不变）
- `build.ps1` 发布校验同步 API 37：SDK 探测由 `android-35` 改为 `android-37.0`，APK badging 校验 `targetSdkVersion` 由 36 改为 37
- Debug 使用独立包名 `com.lamuier.scheduletimeline.debug`（`applicationIdSuffix`），与本机 Release 并存；`.\build.ps1 -Install` 不再覆盖正式版。约定写入 `AGENTS.md` / `docs/technical.md`，启动器显示「追程 Debug」

## 1.8.1 - 2026-08-10

### 修复
- 小米超级岛（issue #2）常驻文案与展开：HyperOS 3 常驻表面是 Android Status Chip，进行中 `shortCriticalText` 改为「团队名+类型」（如 `Star演出`）；`islandFirstFloat` 对齐 ToolKit 默认 `true` 首次自动展开；大岛左右图文分栏（左团队名 / 右类型+开场说明），仍不用会压缩左区的 digit 计时组件
- 修复保存事件后时间轴「跳到第二天」：返回编辑页后 `HorizontalPager` 重组合会瞬态误 `settledPage` 到相邻页，被当成真实翻页改日期。改为用 `isScrollInProgress` 区分真实滑动与重组合抖动，仅真实滑动更新日期

## 1.8.0 - 2026-08-09

### 新增
- 桌面小组件：新增 2×1 紧凑状态卡与 4×3 今日日程列表两种规格，与通知栏共享同一日程状态（复用 `NotificationSchedule` 计算进行中 / 下一项）；事件保存 / 删除 / 导入 / 清空 / 开机后随通知刷新一并更新；颜色资源走 `values` / `values-night`，随系统夜间模式切换；4×3 列表由 `RemoteViewsService` 异步加载当日日程
- 桌面小组件点击直达：2×1 小卡点击进行中 / 下一项事件、4×3 列表点击任意日程项，均直接深链打开对应事件编辑页（冷启动与热启动均生效）；小卡无主事件时仅打开应用

### 变更
- 桌面小组件视觉：根布局与列表项改用圆角卡片背景，演出 / 特典标签也使用圆角背景，颜色随系统夜间模式切换

### 修复
- 修复 2×1 小尺寸小组件文字被截断：收紧 padding / 字号 / 行距，并把空档 / 结束状态的日期移入顶部状态行，让 2×1 高度下只保留两行核心信息
- 修复小尺寸小组件默认显示为 3×1：原 `minWidth=180dp` 被启动器按 `(minWidth+30)/70` 公式算成 3 格宽；降至 `110dp`（正好 2 格），并新增 `widget_small_date_format`（M/d）精简顶部日期，避免窄卡下「2026年08月09日 周日」被截断成丑陋的省略号

## 1.7.0 - 2026-08-09

### 新增
- 时间轴新增「当前时间线」：仅今天页显示一条位于左侧时间轴留白区的红色短线 + 圆点（圆点对齐竖向节点列中心），实时标出「现在」所在位置，随当前时间与列表滚动上下移动；红线不进入卡片内容区，避免横穿卡片；位置由各 item 实际布局测量得出，不依赖卡片高度是否为时间比例，卡片高度逻辑保持不变

### 变更
- 时间轴卡片改为概览模式：概览卡只显示时间、团队/标题、类型，地点 / 关联演出特典 / 重叠警告 / 备注统一收进点击后弹出的底部详情 Sheet；此前因并行组 compact 卡片按时长比例渲染导致关联提示与冲突警告丢失的问题随之恢复（在详情中展示）
- 并行组卡片在 `EventCard` 增加 `fillHeight`，由 `EventLane` 传入使卡片填满 `cardHeight`（时长 × 比例尺），恢复「卡片高度 ∝ 时间」的原始比例逻辑；单卡列表仍按内容高度，缩略信息显示不变
- 进行中进度线由卡片顶部横向线（宽 = 已过占比）改为卡片左侧竖线：浅灰轨道 + 红色填充（`#E53935`），由顶部向下按已过时长占比实时增长
- 详情 Sheet 左下角增加编辑悬浮按钮，点击进入编辑页；卡片点击不再直接跳编辑页
- 进行中的事件在概览卡左侧画一条竖线进度（红线 #E53935），由顶部向下按已过时长占比实时填充，标明当前进行到哪；非今天页不显示进度线
- 进行中的空闲时段在空闲卡片底部画一条进度条（轨道 + 已过占比填充），与进行中事件卡顶部进度线呼应，直观看出当前在空闲段的哪个位置
- 设置页「通知」区下新增「后台保活设置」入口：按 `Build.MANUFACTURER` 检测厂商，优先用 Intent 直接跳到本机厂商的「自启动 / 应用启动管理 / 电池」设置页（覆盖小米 / 华为 / 荣耀 / 三星 / OPPO / realme / vivo / iQOO / 一加 / 魅族），Intent 失败或未识别时回退到 dontkillmyapp.com 对应厂商在线教程；引导用户加白名单，解决厂商杀后台导致的「通知不更新 / 开机后通知失效」
- 编辑页按钮位置互换：保存从列表底部移到右上角图标按钮，删除从右上角移到列表底部（destructive 红样式，更不易误触）；新建事件无删除，底部仍是保存
- Live Updates 进度条刷新间隔改为按事件时长动态计算：把活动窗口均分约 100 段（≈每次刷新推进 1%），并限制在 5 秒 ~ 60 秒之间，兼顾平滑与续航

### 修复
- 修复「下一项日程」开始后通知不切换为「进行中」、倒计时走过 0 变负数：upcoming 通知的 `setTimeoutAfter` 原误用事件结束时间导致通知一直挂到结束，现改为开始时刻，由边界闹钟 refresh 切换为进行中
- 修复进行中事件通知进度条永不更新：`scheduleNextBoundary` 在有进行中事件时按 60 秒周期自调度刷新闹钟，让 `setProgress` / Android 16 `ProgressStyle` 进度随时间推进；空档 / upcoming 状态不周期刷新，避免抢占 Doze 维护窗口影响边界切换
- 修复 Release 编译失败：`BackgroundRestrictionHelper` 中 `guideFor()` 原为 `object` 内的 public 方法却返回 private data class `Guide`，触发 Kotlin "public exposes private-in-class return type" 错误致使 `compileReleaseKotlin` 中断；改为 `private fun guideFor(...)`（仅同文件 `open()` 内部调用，无外部引用）后编译通过
- 修复整轴「当前时间线」不显示：上一版用 `onPlaced` + `parentLayoutCoordinates` 获取 item 位置，坐标空间在 LazyColumn 内易错位/取不到，导致红线实际未画出；改为读取 `LazyListState.layoutInfo.visibleItemsInfo` 中每个可见 item 的视口 `offset`/`size` 直接插值，NowLine 位置与滚动同步
- 修复 Live Updates 进度条精度粗糙：原实现把进度 truncate 到分钟（`/60_000`），5 分钟活动只有 5 档跳动，视觉上明显不准；改为毫秒级进度（`progress`/`duration` 均用毫秒），并把进行中刷新间隔从 60 秒缩短到 30 秒，让进度条更接近真实已过比例
- 修复时间轴左侧时间标签与卡片顶部错位：概览模式把时间标签顶部下移了 12.dp，导致左侧时间刻度与红线/卡片顶不一致；现在去掉该 padding，时间标签与对应卡片顶部严格对齐，「当前时间线」基准与时间刻度一致
- 时间轴左侧每个 item 增加结束时间标签：与开始时间标签分别位于卡片顶部/底部（去除底部 16.dp 留白影响后严格对齐卡片底），使时间轴成为完整起止刻度
- 优化「当前时间线」：由全宽红线改为仅左侧时间轴留白区（时间标签右缘 ~ 卡片左缘）的短红线 + 圆点，不再横穿卡片内容
- 提高 Live Updates 进度条刷新精度：进行中事件的进度刷新间隔由 30 秒缩短到 10 秒；进度值本身已是毫秒级，最小可见变化约为一个刷新周期（10 秒 / 活动总时长）
- 修复「当前时间线」红点不在左侧时间线上：圆点宽 10.dp 但其 offset 用左缘对齐（74.dp），圆心实际落在 79.dp，比节点中心线（74.dp）右偏 5.dp；左移 5.dp 让圆心正好落在节点中心线上
- 修复 Live Updates 进度条一跳就是 20%：原用 `setAndAllowWhileIdle` 的一次性闹钟，在设备低活跃分桶下会被系统批处理/节流到数分钟一次，导致每次可见更新跨度过大；改为 `setExactAndAllowWhileIdle`（精确闹钟不被分桶延迟，需 `SCHEDULE_EXACT_ALARM` 权限），进度条得以按设定间隔平滑推进
- 优化并行轨道内相邻卡片的视觉分隔：同一演出/特典轨道中时间连续的卡片会贴在一起像一块，现在给每张并行卡片底部留 4.dp 间隙，位置与高度比例逻辑不变，仅改善相邻边界的辨识度

### 文档
- 新增 MIT LICENSE，明确开源许可，为仓库公开做准备
- 同步文档至 1.7.0 实际功能：README 补充「当前时间线」「概览 + 详情」；UI-design 更新卡片概览模式与时间轴「当前时间线」语义、并行卡片 4.dp 间隙；hyperisland 文档补充精确闹钟进度刷新（`SCHEDULE_EXACT_ALARM`）与状态刷新时机；technical 版本示例与 buildToolsVersion 说明对齐当前

### 构建
- 新增 `.gitattributes`（`* text=auto eol=lf`）规范化跨平台换行符，并将图片/签名/打包产物标记为二进制，避免协作时 CRLF 冲突或文件损坏
- 全仓库已跟踪文本文件换行符归一化为 LF（与 `.gitattributes` 的 `eol=lf` 对齐），消除 Windows 工作树 CRLF 造成的假性改动
- 固定 `buildToolsVersion = "36.1.0"`（在 `app/build.gradle.kts` 的 `android {}` 块）：本机构建机缺 AGP 8.13 默认的 35.0.0，且离线无法下载，故显式固定到已安装的 36.1.0（与 compileSdk 36.1 一致）以保证 Release 可复现

## 1.6.1 - 2026-08-07

### 修复
- 主屏时间轴并行组（演出/特典双列）卡片高度改为按时长比例渲染：轨道高度等于组的时长跨度（理想 4dp/分钟，超 600dp 上限时自动等比缩小比例尺封顶），卡片按真实起止时间绝对定位，高度 = 时长 × 比例尺并保底最小可读高度（96dp）；修复原先卡片高度由内容多寡决定、导致 90 分钟特典视觉上比 25 分钟演出还短的问题
- 时间轴卡片底色改为类型主色低透明度染色（演出淡紫 / 特典淡粉，浅色 12%、深色 18%），替代原先接近背景的 `surfaceContainerLow`；在并列时间轨道的空白区里卡片边界明显、且保留类型视觉区分，不盖过 chip 与红色冲突提示。改动统一作用于并行组 compact 卡与列表普通卡
