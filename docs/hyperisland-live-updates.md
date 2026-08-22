# 小米超级岛（HyperIsland）与 Live Updates 适配说明

> 整合自 `live-updates-states.md`、`xiaomi-hyperisland.md`、`hyperisland-comparison.md`
> 整合日期：2026-08-07 ｜ 调查基准日：2026-08-05（Xiaomi 17 / HyperOS 3.0.317 / API 36.0）

ScheduleTimeline 的 Live Updates 使用本地 Room 日程数据，**不访问网络**。通知由日程开始/结束边界、应用启动与数据变更触发刷新。整体分两层：

- **Android 16 Live Updates（Status Chip）**：所有 Android 16 设备通用，由系统 promoted ongoing 通道驱动。
- **Xiaomi HyperIsland 超级岛**：仅在 HyperOS OS3 等支持设备上叠加小米 Focus 协议；其**上岛的关键同样是标准 promoted ongoing 请求**，而非小米私有 payload。

**核心结论（2026-08-05 真机验证）**：HyperOS 3 的超级岛由 Android 16 标准 `promoted ongoing` 通道驱动。同机对照正在上岛的 livebridge，其 `focusType=null`（未挂 `miui.focus` payload），靠的是 `flags` 含 `PROMOTED_ONGOING` + `extras` 含 `android.requestPromotedOngoing=true`；我们仅注入 `miui.focus.param` 的通知虽被系统解析为 `focusType=PARAMS`，却因代码把 `setRequestPromotedOngoing` 门在 API 36.1+、36.0 只设 colorized，**从未请求提升**，故不上岛。补上提升请求后，通知立即进入 `Settings.Secure.focus_notifs` 注册表并上岛。MIUI 私有 payload 既不是上岛的充分条件也不是必要条件。

---

## 一、状态总览

| 状态 | 进入条件 | 通知栏表现 | 退出条件 |
|---|---|---|---|
| 已关闭 | 「日程实时通知」关闭，或 Android 13+ 通知权限未授予 | 不显示常驻通知，也不设置边界闹钟 | 打开总开关并授予权限 |
| 无日程 | 总开关已开，但当前没有进行中日程，且「显示未来日程」关闭 | 不显示通知 | 打开「显示未来日程」，或有日程开始 |
| 未来待办 | 「显示未来日程」已开，当前无进行中日程，但存在未来日程 | 显示“下一项”，包含团队/类型、具体日期和开始时间；Android 16 提升成功后，Status Chip 显示系统倒计时 | 到达开始时间、日程被修改/删除，或关闭「显示未来日程」 |
| 单项进行中 | 恰有一项日程处于开始（含）至结束（不含）时间段，且未被标记完成 | 显示该日程的持续通知和进度，详情末尾预告下一项（与「显示未来日程」开关无关）；特典可点「完成」后退出提醒 | 到达结束时间、日程被修改/删除，或特典被标记完成 |
| 多项并行中 | 同时有两项或更多未完成日程进行中 | 显示进行中数量和各项标签，进度覆盖当前活动窗口；并行特典各自提供完成动作 | 所有活动结束、剩余为单项进行中，或特典被标记完成 |
| 无可发布权限 | 系统通知渠道被关闭，或通知权限被撤销 | 主动取消现有通知，并取消下一次边界刷新 | 用户重新允许通知后再次刷新 |

---

## 二、平台适配

### 2.1 Android 16 通用规则

- 工程 `compileSdk 36.1`。所有 Android 16 版本（API 36.0 与 36.1+）都会请求 promoted ongoing：
  - **API 36.1+**：调用公开 `setRequestPromotedOngoing(true)`。
  - **API 36.0（如 HyperOS 3）**：该公开方法尚不存在，改为直接写入 `android.requestPromotedOngoing=true` extra（与 NotificationCompat 做法一致，已在 Xiaomi 17 / HyperOS 3.0 真机验证可获得 `PROMOTED_ONGOING`）。
  - **任何情况下都不再请求 colorized**。只有系统实际授予 promoted ongoing 标志时，才会出现 Status Chip。
- **Status Chip 内容按状态设置**：进行中日程 `setShortCriticalText("进行中")`。未来日程距开始 2 小时内不设短文本，由系统按 `setWhen()` 显示实时倒计时；超过 2 小时系统会回退显示完整标题导致胶囊溢出（HyperOS 3 实测），此时改设紧凑短文本——当天「12:20 开场/开始」、跨天「周六 12:20」。短文本优先级高于时间；最终样式与是否展示仍由系统决定。
- **API 36.1+ 非通话通知提升资格**：须满足主动请求提升、`ongoing`、有标题、非分组摘要、无自定义 `RemoteViews`、允许的通知样式且未请求 colorized；资格成立也不保证系统一定显示 Status Chip，用户或通知渠道设置仍优先。
- **API 26–35（Android 8–15）**：回退为普通持续通知，仍保留倒计时、进度条和开始/结束边界刷新。
- **API 13+**：首次开启总通知时请求 `POST_NOTIFICATIONS`；拒绝权限不会伪造通知状态。
- **API 37（Android 17）进度条**：常驻通知仍用 `ProgressStyle` 显示进度，**不再**用语义色区分进行中 / 空档（去掉 `contentText` 语义注解与进度段 `setSemanticStyle`），正文保持默认文字颜色。不调用 `setColor()`。

### 2.2 Xiaomi HyperOS OS3

- **适配依据**：小米超级岛开发指南（以小米官方文档为准）。
- **启用/检测条件**（仅当全部满足才注入 HyperIsland 协议字段；否则走普通 Live Updates）：

  | 信号 | 要求 |
  |---|---|
  | 设备厂商 | `Xiaomi`、`Redmi` 或 `POCO` |
  | 岛功能属性 | 系统属性 `persist.sys.feature.island = true` |
  | 协议版本 | `Settings.System` 的 `notification_focus_protocol` 不是已知的 OS1/OS2（读不到时按 OS3 候选处理）；内容提供器不可读时仍继续发送普通通知协议 |
  | Focus 通知查询 | `content://miui.statusbar.notification.public` 的 `canShowFocus`，**仅作诊断，不是硬门槛**（见第七节：真机已证伪其为上岛必要条件） |

  协议值读不到时按 OS3 候选处理；不申请白名单、不接入 MiPush。

- **上岛机制**：OS3 使用普通 Android 通知作承载，应用在同一 `LIVE_NOTIFICATION_ID`（`4101`）上更新 `miui.focus.param`（标准 `param_v2`）和 `miui.focus.pics`。JSON 使用 HyperIsland-ToolKit 同款 `protocol=3` 与 `updatable=true`，边界刷新不会产生重复通知。该 payload 作为旧版 OS3 表面的兼容保留，但**上岛的关键是 promoted ongoing 请求**（见核心结论）。
- **胶囊/岛渲染**：HyperOS 3 常驻岛表面实际是 Android Status Chip（`shortCriticalText` + `setWhen()` 计时）；进行中短文案写「团队名+单字类型」（如 `Star演`），不再写死「进行中」。MIUI 大岛对齐 ToolKit `setBigIslandInfo` 左右图文：A 区图标 + 团队名，B 区 `imageTextInfoRight` 类型 +「后开场/已开场」——**仍不挂 digit 计时组件**（会压缩 A 区丢左文字）。`enableFloat=true` 且 `islandFirstFloat=true`（ToolKit 默认）首次自动展开，否则 `airtimeCount` 会一直为 0、用户永远看不到大岛文字。小岛使用 256px 彩色图标（`drawable-nodpi/ic_island.png`）。
- **最终呈现由系统决定**：HyperOS SystemUI 决定尺寸、动画、是否显示在顶部状态栏及用户权限；应用不伪造 HyperIsland 状态。关闭通知、无可发布权限或无下一项时，取消同一通知 ID。
- **实现约束**：直接生成标准模板 payload，不依赖 Kotlin 2.2 编译的第三方二进制，保持工程 Kotlin 2.0.21 基线；不使用自定义 `RemoteViews`，也不设置 Xiaomi `timeout` 字段以避免分钟/秒单位差异。

---

## 三、Payload 映射（状态 → 内容）

每次刷新复用 `ScheduleNotificationCoordinator.LIVE_NOTIFICATION_ID`（`4101`）：

| 日程状态 | HyperIsland 内容 |
|---|---|
| 未来待办 | `baseInfo` 显示“下一项”标题、团队/类型、具体日期和开始时间；大岛左团队名、右「类型 / 后开场」；远未来 Status Chip 用紧凑短文案 |
| 单项进行中 | `baseInfo` 显示当前团队和结束时间；大岛左团队名、右「类型 / 已开场」；Status Chip `shortCriticalText` = 团队名+类型 |
| 多项并行中 | Android 通知正文显示全部活动，HyperIsland 展示当前通知标题和合并计时窗口 |
| 无下一项 / 通知关闭 | 取消 `4101`，不留下 HyperIsland extras |

标准 payload 资源：

```text
miui.focus.param  -> {"param_v2": ...}
miui.focus.pics   -> miui.focus.pic_schedule        # 注：pic_ticker 已在调查期移除（未引用）
```

`param_v2` 固定写入 `protocol=3`、`updatable=true`。（历史说明：早期 spec 曾计划写入 `notifyId=4101`、`orderId=schedule_live`、单调递增 `sequence` 与 `tickerPic`，但调查期核对 ToolKit 序列化后确认这些字段非必需，已**全部删除**；`param_v2` 模型中存在这些可选字段不影响，省略即安全。）

---

## 四、Payload 结构对齐验证（与 HyperIsland-ToolKit 逐字段核对）

以本工程 `buildJsonParam()` 输出对照 ToolKit kotlinx 序列化（`encodeDefaults=true, explicitNulls=false`）实际输出，确认 JSON 不是上岛瓶颈：

| 字段 | ToolKit 输出 | 我们的输出 | 结论 |
| --- | --- | --- | --- |
| 根结构 | `{"param_v2": {...}, "isShowNotification": true}` | 同款 | ✅ |
| protocol | 3（ParamV2 默认值，会序列化） | 3 | ✅ |
| updatable | true（默认值，会序列化） | true | ✅ |
| business / ticker | 构造参数 | `schedule_timeline` / title | ✅ |
| enableFloat / islandFirstFloat | demo 默认 true/true（builder 字段） | 测试通知 true/false，live 通知 true/false | ✅ 参数化合理 |
| baseInfo | type/title/subTitle/content/picFunction | 同款 | ✅ |
| picInfo | type/pic（loop/autoplay/number 可选） | 1/ref（移除未引用的可选字段） | ✅ |
| param_island | islandProperty/islandPriority/islandOrder/dismissIsland/maxSize/needCloseAnimation | 全对齐 | ✅ |
| imageTextInfoLeft | type/picInfo/textInfo(title[/content]) | 左区图标 + 团队名 title | ✅ |
| imageTextInfoRight | type=2 + textInfo | 右区类型 title + 开场说明 content（非 digit） | ✅ 对齐 ToolKit 左右图文 demo |
| 右侧 digit 计时 | 无 | 已移除（实测会压缩 A 区丢左文字） | ✅ 规避 issue #2 |
| islandFirstFloat | demo 默认 true | true（首次自动展开） | ✅ |
| Status Chip 短文案 | — | 进行中 = 团队名+类型 | ✅ 常驻表面可见身份 |
| 已删除字段 | notifyId/orderId/tickerPic 在模型中可选，`sequence` 不存在 | 已全部删除 | ✅ 安全 |

payload 结构与字段值均无出入，可排除 JSON 解析失败的假设。

---

## 五、关键时间点提醒

与常驻 Live Updates **相互独立**：只受「日程实时通知」总开关与通知权限约束，**不受「显示未来日程」开关影响**。每项日程最多触发三次普通提醒（独立渠道 `schedule_reminders`，`IMPORTANCE_HIGH`）：

| 触发点 | 触发时刻（本地墙钟） | 文案 |
|---|---|---|
| 开始前 3 天 | 与开始同一时刻（如 22:00 开始则 3 天前 22:00） | 「3 天后（yyyy年MM月dd日）HH:mm 开始」 |
| 当天 0 点 | 日程日 00:00 | 「今天 HH:mm 开始」 |
| 开始前 1 小时 | 开始时刻减 1 小时 | 「1 小时后（HH:mm）开始」 |

- 提醒为普通通知：非常驻、不请求 promoted ongoing、不注入超级岛 payload，`setAutoCancel(true)` 且 `setTimeoutAfter(5 分钟)` 自动消失；通知 ID 按「事件 id × 提醒类型」派生。
- 任意时刻只挂最近一个触发点的 `AlarmManager` 闹钟（`setAndAllowWhileIdle`，携带 eventId 与提醒类型）；触发时按 eventId 读最新数据发通知（事件已删除则跳过），随后 `refresh()` 推进到下一触发点。保存/删除/导入/清空/开机后都重算。
- 已错过的触发点静默跳过不补发（如临开场 30 分钟才录入的事件不会收到 3 天前与 0 点提醒）；触发时刻按 `ZonedDateTime` 偏移计算，与本地墙钟对齐。
- **特典完成**：详情页「完成日程」或 Live Update 通知动作会把该特典标为已完成。完成后不再进入常驻通知、进度刷新与关键时间点提醒（已弹出的提醒一并取消）；演出无此按钮。

---

## 六、状态刷新时机

1. 应用启动或回到前台时读取全部本地日程并刷新。
2. 保存、删除、批量导入、清空数据后立即刷新。
3. 到达最近的开始/结束边界时由 `AlarmManager` 唤醒并刷新。
4. 进行中事件按事件时长动态间隔（约 1%/步，5–60 秒）由精确闹钟（`setExactAndAllowWhileIdle`，需 `SCHEDULE_EXACT_ALARM`）周期刷新进度；空档与未来待办不周期刷新，避免抢占 Doze 维护窗口影响边界切换。
5. 关闭总通知时同时关闭「显示未来日程」，并取消边界闹钟与提醒闹钟。

---

## 七、调查与对比研究（方法 & 历史）

> 本节保留「为什么当前实现长这样」的调查过程与三方对照，便于后续维护者复现推理。调查基准日 2026-08-05；症状为「小米设备发通知只弹悬浮通知，不展开超级岛」。

### 7.1 结论速览（按可疑度排序，标注实施结果）

| 优先级 | 差异 | 当时现状 vs 参考项目 | 影响 | 实施结果 |
| --- | --- | --- | --- | --- |
| **P0** | 通知渠道 importance | 我们 `IMPORTANCE_DEFAULT`；ToolKit / livebridge 均 `IMPORTANCE_HIGH` | 超级岛依附高优先级渠道；DEFAULT 大概率被忽略岛 extras，且渠道创建后 importance 被系统锁定 | ✅ **已实施**：升级 HIGH 并换新渠道 ID `schedule_live_updates_v2`（旧 `schedule_live_updates` 已删除） |
| **P0** | `canShowFocus` 焦点通知授权是否作硬门槛 | 我们放宽不作为硬门槛；ToolKit `isSupported()` 把它当硬门槛 | 假设授权为 false 时 MIUI 静默丢弃 payload | ❌ **真机证伪**：Xiaomi 17 上 livebridge `focusType=null` 仅靠 promoted ongoing 上岛；本设备授权不影响上岛，保留为诊断信息不门槛 |
| **P1** | 前台/后台测试姿势 | 前台点测试按钮（已移除）；岛依赖 `islandFirstFloat=true` | 已对齐，非瓶颈；live 通知（false/false）需退后台才可能展岛 | 验证姿势说明见下 |
| **P2** | `pics` 多塞未引用的 `miui.focus.pic_ticker` | 有但 param 未引用 | 多余资源，理论无害 | ✅ **已实施**：清理未引用 pic_ticker |

**核心推理（调查时）**：两家参考项目的唯一共同硬配置是 `IMPORTANCE_HIGH` 渠道 + `canShowFocus` 硬门槛；我们恰好都不同（DEFAULT 渠道 + 放宽授权），payload 已对齐到字段级。后被真机验证修正为：真正必要的只有 **promoted ongoing 请求**，焦点授权与私有 payload 均非必要。

### 7.2 三方实现对比

**通知渠道**

| 项 | HyperIsland-ToolKit demo | livebridge | 追程（本项目） |
| --- | --- | --- | --- |
| 渠道 ID | `hyperisland_demo_channel` | 按类型多个渠道 | `schedule_live_updates_v2`（原 `schedule_live_updates` 已清） |
| importance | **IMPORTANCE_HIGH** | **IMPORTANCE_HIGH** | **IMPORTANCE_HIGH**（由 P0-1 升级） |
| 声音/振动 | 系统默认 | 静音渠道 `setSound(null)` + `enableVibration(false)`，有声渠道单独建 | 系统默认 |
| 锁屏可见性 | 默认 | 跟随用户偏好 | 默认 |

> ⚠️ **渠道锁定陷阱**：渠道一旦创建，importance 由系统/用户接管，应用再 `createNotificationChannel` 同名渠道无效。已装设备必须换渠道 ID 或引导用户在系统设置手动开启，否则改动永远不生效。测试机务必卸载重装或清数据后验证。

**通知本体（Builder 配置）**

| 项 | ToolKit demo | livebridge | 追程 |
| --- | --- | --- | --- |
| Builder | NotificationCompat | NotificationCompat | 框架 `Notification.Builder` |
| smallIcon | ✅ | ✅ | ✅ `ic_launcher_monochrome` |
| setOngoing | ❌ 未设 | ✅ true | ✅ true |
| setAutoCancel | 默认 | false | 默认 |
| setOnlyAlertOnce | ❌ | ✅ true | ✅ true |
| setCategory | ❌ 未设 | `CALL`/`PROGRESS`/`STATUS` 按场景 | `CATEGORY_EVENT` |
| setPriority | ❌ 未设 | `PRIORITY_HIGH` | ❌ 未设 |
| setRequestPromotedOngoing | ❌ | ✅（Android 16 Live Updates） | ✅（36.1+ 公开；36.0 写 extra） |
| setSilent | ❌ | 静音渠道时 `setSilent(true).setDefaults(0)` | ❌ |
| contentIntent | ✅ | ✅ | ✅ |

ToolKit demo 通知极简，连 ongoing/category 都不设，仅靠 `IMPORTANCE_HIGH` 渠道 + `miui.focus.*` extras 即可上岛——说明 ongoing/category/priority 均非上岛必要条件。

**小米 extras**

三方一致，均通过 `buildResourceBundle()` 等价物 + JSON 字符串：

```text
extras["miui.focus.actions"] = Bundle（action key → Notification.Action）
extras["miui.focus.pics"]    = Bundle（"miui.focus.pic_<key>" → Icon）
extras["miui.focus.param"]   = JSON 字符串（{"param_v2": {...}, "isShowNotification": true}）
```

**系统支持检测**

| 检测项 | ToolKit | livebridge | 追程 |
| --- | --- | --- | --- |
| 厂商 | `Xiaomi`（忽略大小写） | 复用 ToolKit | Xiaomi / Redmi / POCO 三选一（更宽） |
| 岛特性 | 反射 `persist.sys.feature.island` | 复用 ToolKit | 同款 + 字符串回退 |
| 焦点授权 | `canShowFocus` **硬门槛** | 复用 ToolKit，硬门槛 | 查询但**不作硬门槛**（诊断用） |
| 协议版本 | 无 | 无 | 非 OS1/OS2 即按 OS3（额外门槛，来源待考；设置项不存在默认 0 可通过） |

**Manifest**

| 权限/声明 | ToolKit demo | livebridge | 追程 |
| --- | --- | --- | --- |
| POST_NOTIFICATIONS | ✅ | ✅ | ✅ |
| POST_PROMOTED_NOTIFICATIONS | ❌ | ✅ | ✅ |
| 其他 MIUI 私有权限 | 无 | 无 | 无 |
| SCHEDULE_EXACT_ALARM | ❌ | ❌ | ✅（1.7.0 起，进行中进度条精确刷新） |

Manifest 差异：本工程额外声明 `SCHEDULE_EXACT_ALARM` 以支持精确闹钟刷新进度条；小米焦点通知本身不要求额外声明权限。

### 7.3 假设的验证顺序（建议保留）

1. 先做 P0-1（渠道 HIGH + 换 ID），真机重装验证 → 大概率解决。
2. 若仍不上岛，确认 promoted ongoing 是否真的写入（dumpsys 看 `flags`/`extras`）；Focus 授权只作诊断。
3. 两步确认后仍不上岛，用 dumpsys 确认 extras 是否完整挂上，再回头怀疑 payload。

---

## 八、调试检查与验证手段

### 8.1 系统侧检查（HyperOS）

1. 在 HyperOS 系统设置中开启应用通知；可额外开启 Focus / HyperIsland 权限以提高系统展示机会，但应用不以此为前置条件。
2. 打开应用「更多 → 通知 → 显示未来日程」。
3. 准备一个未来日程或正在进行的日程，触发前台刷新或等待边界闹钟。
4. 若顶部未出现 HyperIsland，先确认系统协议版本是否为 OS3 候选；最终展示仍受系统版本、渠道设置和用户策略控制。
5. 可选：在测试结果 UI 提供跳转系统通知设置入口（`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` 带 channel id extra）。

### 8.2 adb 验证命令

```bash
# 发通知后看 extras 是否带上 miui.focus.param（确认 payload 真的挂上）
adb shell dumpsys notification --noredact | grep -A 30 "com.lamuier.scheduletimeline"

# 直接读系统属性确认岛特性
adb shell getprop persist.sys.feature.island

# 读协议设置（确认协议检测不误判）
adb shell settings get system notification_focus_protocol

# 确认通知是否进入焦点注册表（上岛标志）
adb shell settings get secure focus_notifs
```

---

## 附：代码出处速查

| 内容 | 位置 |
| --- | --- |
| 渠道创建 / importance / 换 ID | `notification/ScheduleNotificationCoordinator.kt`（ensureChannel） |
| Builder 配置 / promoted ongoing 注入 | `ScheduleNotificationCoordinator.kt` |
| HyperIsland extras 组装 | `XiaomiHyperIslandAdapter.kt`（buildResourceBundle / buildJsonParam） |
| 设备能力检测（含 Focus 查询非门槛） | `XiaomiHyperIslandCapability.kt` |
| 边界/提醒闹钟调度 | `notification/*`（NotificationSchedule 相关） |
| 三方参考 | `C:\Users\lamuier\Code\HyperIsland-ToolKit`、`C:\Users\lamuier\Code\livebridge` |
