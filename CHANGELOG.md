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

### 文档
- 新增 MIT LICENSE，明确开源许可，为仓库公开做准备

### 构建
- 新增 `.gitattributes`（`* text=auto eol=lf`）规范化跨平台换行符，并将图片/签名/打包产物标记为二进制，避免协作时 CRLF 冲突或文件损坏
- 全仓库已跟踪文本文件换行符归一化为 LF（与 `.gitattributes` 的 `eol=lf` 对齐），消除 Windows 工作树 CRLF 造成的假性改动

## 1.6.1 - 2026-08-07

### 修复
- 主屏时间轴并行组（演出/特典双列）卡片高度改为按时长比例渲染：轨道高度等于组的时长跨度（理想 4dp/分钟，超 600dp 上限时自动等比缩小比例尺封顶），卡片按真实起止时间绝对定位，高度 = 时长 × 比例尺并保底最小可读高度（96dp）；修复原先卡片高度由内容多寡决定、导致 90 分钟特典视觉上比 25 分钟演出还短的问题
- 时间轴卡片底色改为类型主色低透明度染色（演出淡紫 / 特典淡粉，浅色 12%、深色 18%），替代原先接近背景的 `surfaceContainerLow`；在并列时间轨道的空白区里卡片边界明显、且保留类型视觉区分，不盖过 chip 与红色冲突提示。改动统一作用于并行组 compact 卡与列表普通卡
- 移除并行组 compact 卡片上误标的「同团队时段」chip（`R.string.event_shared_team` 一并删除）：业务上同一团队的演出与特典不会时间重叠，能进并行组的演出/特典必然不同团队，该标签语义恒错；双列布局本身已表达「并存」，无需冗余文字。普通列表卡的「关联演出/特典」行（基于团队名匹配、不要求重叠）保持不动

### 文档
- 修正 README、`AGENTS.md`、`docs/UI-design.md`、`docs/xiaomi-hyperisland.md`、`docs/hyperisland-comparison.md` 中过时的通知设置命名与已移除调试按钮描述：开关名统一为「显示未来日程」、删除「测试 Status Chip」/「测试超级岛上岛」按钮说明、修正 promoted ongoing 说法（Android 16 及以上统一请求提升，不再走 colorized 兼容路径）
- 删除根目录小米官方模板库 PDF 与 `deliverables/` 两份代码评审文档（非活文档、不在提交清单）

### 构建
- 移除图标生成工具 `tools/process_launcher_icon.py` 及 `assets/` 全部源图与预览图（`app_icon_source*.png`、`ic_launcher_1024.png`、`ic_launcher_*_preview*.png`）；启动图标产物已提交于 `res/`（mipmap-* 与 drawable-*），不再需要生成器，删除不影响构建

---

## 1.6.0 - 2026-08-05

### 新增

- 关键时间点提醒：每项日程在开始前 3 天（与开始同一时刻）、当天 0 点、开始前 1 小时各发一条普通提醒通知（独立「日程提醒」渠道），跟随总通知开关、与「显示未来日程」开关无关；非常驻、不请求 Live Updates 提升，5 分钟后自动从通知栏消失；已错过的触发点不补发，事件保存 / 删除 / 导入 / 清空 / 开机后自动重算调度
- 设置页增加「测试 Status Chip」按钮：发送独立的 5 分钟倒计时通知，检查系统 Live Updates 权限、通知提升资格及实际 `FLAG_PROMOTED_ONGOING` 结果
- 设置页增加「测试超级岛上岛」按钮：在不影响真实日程通知的前提下发送独立的 5 分钟倒计时超级岛测试通知，报告设备支持、焦点通知授权与发送结果；超级岛测试使用独立 `notifyId` / `orderId`，与 Live Updates 通知互不干扰
- Xiaomi HyperOS OS3 适配标准 HyperIsland：未来日程倒计时、进行中正计时和同一通知 ID 实时更新

### 变更

- 设置页「通知栏常驻 Live Updates」开关更名为「显示未来日程」，描述改为「开启后，没有进行中的日程时显示下一项未来日程；关闭后只显示当前日程」（行为不变，仅文案更准确）
- 进行中的日程通知详情末尾追加下一项预告（如「StarDiary演出 · 至 11:00｜下一项：终特 12:30」），与「显示未来日程」开关无关；下一项不在今天时带星期前缀
- 移除设置页「测试 Status Chip」「测试超级岛上岛」两个调试按钮，连同 `ScheduleViewModel`/`ScheduleNotificationCoordinator` 的测试方法、测试结果枚举、`XiaomiHyperIslandAdapter.applyTest`、专用通知 ID/常量和 23 个字符串资源一并清理；payload 的 `enableFloat`/`islandFirstFloat` 随之固定为 false
- 超级岛摘要态（胶囊）按官方《小米超级岛模板库》重做：采用「图文组件1 + 等宽数字文本组件」模板——A 区（摄像头左侧）彩色应用图标 + 团队名大字 + 类型小字，B 区（右侧）倒计时/正计时数字 + 「后开场/已开场」后缀；多项日程并行时 A 区显示「N 项日程」；岛图标由单色剪影改为 256px 彩色图标（新增 `drawable-nodpi/ic_island.png`），解决黑胶囊上白色色块不可读的问题
- `XiaomiHyperIslandAdapter.buildJsonParam` 解耦 `ScheduleEvent`，改为接收 `subTitle`、`notifyId`、`orderId` 参数，使超级岛 payload 可用于独立测试通知；live 与测试通知各自携带稳定身份，避免更新串台
- Live Updates 通知渠道 importance 由 `IMPORTANCE_DEFAULT` 提升为 `IMPORTANCE_HIGH` 并将渠道 ID 迁移为 `schedule_live_updates_v2`（渠道创建后 importance 被系统锁定，旧渠道 `schedule_live_updates` 在启动时自动删除重建），对齐 HyperIsland-ToolKit demo 与 livebridge——小米焦点通知/超级岛只依附于高优先级渠道，这是修复"只弹悬浮不上岛"的关键改动

### 修复

- 左右滑页切日不连贯修复：`ScheduleViewModel.observeDayState` 改为按日期 LRU 缓存的共享 `StateFlow`（保留最近 12 天），分页回访时初始值即上次已加载数据，消除每页从空态闪变到真实内容的跳变；顶栏日期改为跟随拖动——滚动中显示目标页日期、静止时显示落定页日期，不再等分页落定后才跳变
- 超级岛真机根因修复（Xiaomi 17 / HyperOS 3.0.317 / API 36.0 实测）：HyperOS 3 的超级岛由 Android 16 标准 promoted ongoing 通道驱动而非 MIUI 私有 payload——同机 livebridge 上岛通知 `focusType=null` 且带 `PROMOTED_ONGOING`，而我们仅注入 `miui.focus.param` 的通知被解析为 `focusType=PARAMS` 却不上岛。此前 `setRequestPromotedOngoing` 仅在 API 36.1+ 调用，36.0 只设 colorized，导致 HyperOS 3 设备从未请求提升。现在所有 API 36+ 都请求 promoted ongoing：36.1+ 走公开方法，36.0 直接写入 `android.requestPromotedOngoing=true` extra（对齐 NotificationCompat 行为）；任何版本都不再叠加 colorized。修复后通知进入系统 `focus_notifs` 注册表并上岛
- 胶囊文字过长修复：距开始超过 2 小时的未来日程，胶囊不再回退显示完整通知标题（如「下一项：ReaLume演出」溢出截断），改为紧凑短文本——当天「12:20 开场/开始」、跨天「周六 12:20」；2 小时内仍由系统显示实时倒计时
- 胶囊左区图标隐形修复：状态栏小图标是黑色单色剪影，在黑色胶囊上不可见；三条通知（live、Status Chip 测试、岛测试）统一补设彩色 `largeIcon`，胶囊左区显示彩色应用图标
- 通知小图标新增 `drawable-nodpi/ic_notification.png`：透明底、裁切居中后的彩色时间轴图形（品牌紫 #A689F7）。HyperOS 3 胶囊左区按原色渲染 smallIcon 且不染色——原黑色剪影不可见、白色剪影粗糙，彩色图形在胶囊上直接显示彩色；状态栏仍按 alpha 染色显示剪影，不受影响
- 「测试超级岛上岛」测试通知同步请求 promoted ongoing（此前只注入 MIUI payload）；「测试 Status Chip」门槛由 Android 16 QPR2（36.1）放宽为 Android 16（36）
- Android 16 常驻 Live Updates 的未来日程不再用“进行中”短文本覆盖时间信息，Status Chip 改由系统根据开始时间显示倒计时；进行中的日程仍显示“进行中”
- Android 16 QPR2（API 36.1）请求 promoted ongoing 时不再同时请求 colorized，避免通知被新版系统判定为不具备 Status Chip 提升资格
- HyperIsland 兼容检测增加协议版本、系统岛开关和 Focus 状态探测；Focus 白名单不作为硬门槛，不支持时保持 Android Live Updates 回退
- 超级岛 payload 移除 `notifyId`、`orderId`、`sequence`、`tickerPic` 四个 HyperIsland-ToolKit 不输出的多余字段（`sequence` 不在 ToolKit ParamV2 模型中），对齐参考实现避免系统 JSON 解析器因未知字段拒绝 payload；`enableFloat`/`islandFirstFloat` 改为参数化，测试通知用 `true` 对齐 ToolKit demo 默认让岛首次浮出展开
- 超级岛资源 bundle 移除未被 payload 引用的 `miui.focus.pic_ticker` 图片，与 ToolKit 标准模板行为一致

### 文档

- 新增 `docs/xiaomi-hyperisland.md`，记录 OS3 启用条件、payload 映射、状态和回退边界
- 新增 `docs/hyperisland-comparison.md`，记录与 HyperIsland-ToolKit / livebridge 的逐层对比研究、差异结论与真机验证步骤

### 构建

- `targetSdk` 由 35 更新为 Android 16（API 36）；`compileSdk` 保持 Android 16 QPR2（36.1），Release APK 校验同步更新
- 发布版本由 `1.5.2` 更新为 `1.5.3`（`versionCode` 由 8 递增至 9）
- 发布版本由 `1.5.3` 更新为 `1.5.4`（`versionCode` 由 9 递增至 10）
- 发布版本由 `1.5.4` 更新为 `1.5.5`（`versionCode` 由 10 递增至 11）
- 发布版本由 `1.5.5` 更新为 `1.5.6`（`versionCode` 由 11 递增至 12）
- 发布版本由 `1.5.6` 更新为 `1.6.0`（`versionCode` 由 12 递增至 13）

---

## 1.5.2 - 2026-08-04

### 修复

- Android 16 Live Updates 显式启用 colorized 通知以满足 promoted ongoing 资格；API 36.1+ 请求系统 Status Chip，API 36 缺少提升方法时仍保留 ProgressStyle 和短文本

### 构建

- 编译 API 对齐 Android 16 QPR2（36.1），升级至 AGP 8.13.2 / Gradle 8.13；Live Updates 改为直接调用 API 36 / 36.1，`targetSdk` 保持 35
- Gradle Wrapper 与 Maven 依赖优先使用华为云国内镜像；固定 Gradle 官方 SHA-256 校验值，并保留官方依赖仓库作为回退
- 版本号由 `1.5.1` 更新为 `1.5.2`（`versionCode` 由 7 递增至 8）

---

## 1.5.1 - 2026-08-04

### 修复

- 演出与特典重叠时固定为两条独立时间轴线：演出列只显示演出，特典列只显示特典；同类型多项不再交错到另一列
- 常驻通知展示未来日程时增加具体日期，避免跨日时只显示时间造成歧义

### 文档

- 新增 `docs/live-updates-states.md`，说明 Live Updates 的关闭、无日程、未来待办、单项进行中、多项并行和平台回退状态
- 按实际发布批次归档并分类 `1.3.0`、`1.4.0`、`1.5.0` 的更新记录

### 构建

- 版本号由 `1.5.0` 更新为 `1.5.1`（`versionCode` 由 6 递增至 7）

---

## 1.5.0 - 2026-08-04

### 新增

- 日程支持开始提醒和进行中持续通知；Android 16 使用 `ProgressStyle` / promoted ongoing 适配 Live Updates，低版本自动回退
- 设置新增「通知栏常驻 Live Updates」选项；有当前 / 未来日程时持续显示，空档期展示下一项倒计时
- 平特与演出等跨类型重叠日程改为双线并显，可分别点击编辑

### 变更

- 左右拖动切日改为跟手位移，松手后按距离吸附返回或完成切换
- 设置页新增日程通知开关；保存、删除、导入、清空及设备重启后自动刷新通知调度

### 构建

- Release APK 权限校验白名单同步纳入通知与开机重调度所需权限
- 版本号由 `1.4.0` 更新为 `1.5.0`（`versionCode` 由 5 递增至 6）

---

## 1.4.0 - 2026-08-04

### 新增

- 日期切换增加左右滑动动效；查看非今天日期时，左下角显示「返回今天」悬浮按钮
- 特典支持选择或录入多个团队；同日演出与特典按团队名称交集自动关联

### 变更

- 主屏「追程」标题移至左上角，日期导航独立显示在标题下方
- 移除编辑页手工绑定演出 / 特典的操作；CSV 不再导出关联演出时间，旧 10 列格式仍兼容导入

### 构建

- 版本号由 `1.3.0` 更新为 `1.4.0`（`versionCode` 由 4 递增至 5）

---

## 1.3.0 - 2026-08-04

### 变更

- 演出可勾选关联当日特典（显示开始–结束）；列表在演出卡片上展示关联特典时段
- 当日无日程时提示距最近日程还有几天（可点跳转）；非当天不显示「正在进行」横幅
- 首次安装不再写入样例日程
- 批量导入样例含表头；解析时自动跳过标题行

### 构建

- `build.ps1` 支持 `-Help` / `--help`；启动时打印生效参数与实际 Gradle 命令行
- 版本号由 `1.2.0` 更新为 `1.3.0`（`versionCode` 由 3 递增至 4）

---

## 1.2.0 - 2026-08-04

### 变更

- 日程改为「演出 / 特典」：特典含前特·平特·终特；必填团队；特典可关联当日演出
- 主屏仅保留列表时间轴，移除图表视图与视图切换
- 支持左右滑动或顶栏箭头切换日期（仍可点击日期打开选择器）
- ABI 仅打包 `arm64-v8a`（不再包含 x86_64 / 32 位）
- Manifest 安全默认值：`allowBackup=false`、`usesCleartextTraffic=false`、`fullBackupContent=false`，并增加 `data_extraction_rules.xml`
- Room 升至 v4（`team` / `eventType` / `tokutenKind` / `linkedPerformanceId`）

### 构建

- 对齐 AndroidApkTemplate：统一入口改为 `build.ps1`（替换 `build-*.bat`）
- Release 签名改为 `ANDROID_RELEASE_*` 环境变量注入；本机元数据在 `%LOCALAPPDATA%\ScheduleTimeline\signing\`
- Release 产物命名改为 `dist/ScheduleTimeline-v{version}-release.apk`
- 增加 ABI 过滤与根目录 `lint.xml`

### 文档

- 精简 README：去掉 Android Studio 安装/入门教程与冗长操作说明，保留功能、构建、CSV 格式与结构速查
- 新增 `docs/UI-design.md`（色彩、字体、间距、组件与时间轴视觉约定）；`AGENTS.md` / `README` 增加入口
- `AGENTS.md` / `README` 同步模板构建、签名与 SemVer 约定

---

## 1.1.0 - 2026-07-12

### 变更

- 批量导入：地点与备注可留空；导入样例支持长按复制
- 批量导入 / 导出增加日期列（`yyyy-MM-dd`）；日期留空则落到当前选中日

### 文档

- 同步批量导入格式说明（日期列、可空字段、长按复制样例）

### 构建

- GitHub Releases 发布 `ScheduleTimeline-1.1.0-release.apk`（当前为 debug 签名）

---

## 1.0.0 - 2026-07-12

### 新增

- 图表 / 列表双视图时间轴，支持缩放与空闲、冲突提示
- 日程分类管理（含长按删除）与按日编辑
- CSV 批量导入、导出当日、一键清空本地数据
- 数据管理底栏 UI（分组操作、导入预览、清空确认）
- Room 本地库、schema 导出与正式 Migration
- JVM 单测（`TimelineBuilder` / `ChartLayout` / `ScheduleExport`）
- `build-debug.bat` / `build-release.bat` 本地打包脚本
- 助手约定文档 `AGENTS.md`

### 变更

- 应用显示名改为「追程」
- 支持浅色 / 深色 / 跟随系统主题（设置页切换，本地持久化）
- 启动图标恢复第一版时间轴卡片风格：白底；主体明显缩小并加 inset 留白，适配圆形 / 正方形 / 四圆角

### 修复

- release 构建：补回 `Modifier` 导入，图标仅使用 `material-icons-core`

### 文档

- 约定每次更新写入 `CHANGELOG.md`：先入未归档，人工归档后再形成版本

### 构建

- GitHub Releases 发布 `ScheduleTimeline-1.0.0-release.apk`（当前为 debug 签名）
