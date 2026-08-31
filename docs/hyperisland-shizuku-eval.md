# 评估：Shizuku 绕过超级岛白名单，并继续适配岛样式

> 决策记录。评估日：2026-08-31。
> 对照基准：本仓库现有 HyperOS 3 适配（[hyperisland-live-updates.md](hyperisland-live-updates.md)，真机 2026-08-05：Xiaomi 17 / HyperOS 3.0.317 / API 36.0）与小米官方开发指南 / Q&A（2026-01 / 2025-10）。

**结论：不接入 Shizuku，不绕过小米焦点通知白名单。** 超级岛样式继续走现有客户端 `miui.focus` payload + Android 16 promoted ongoing；白名单如需完整焦点权限，只走官方申请，且与当前「无网络、无推送 SDK」产品定位冲突，近期不做。

---

## 1. 要解决的其实是两件不同的事

| 议题 | 用户感知 | 真正卡点 | 是否依赖白名单 |
|---|---|---|---|
| 通知出现在摄像头旁的「岛」位置 | 状态栏胶囊 / Status Chip | Android 16 `requestPromotedOngoing` | **否**（本机已证伪） |
| 通知按小米模板渲染（大岛 A/B 分栏、小岛图标、AOD、岛内按钮） | 展开后的超级岛卡片 | SystemUI 是否消费 `miui.focus.param` | **多数机型要焦点权限** |
| 应用出现在系统「焦点通知」开关列表 | 设置里能单独开关 | 小米云端 / 框架白名单 | **是** |

本应用已经同时做了前两层：`ScheduleNotificationCoordinator` 请求 promoted ongoing；`XiaomiHyperIslandAdapter` 注入 OS3 `param_v2`。2026-08-05 真机结论是：**上岛靠 promoted ongoing，MIUI payload 既非充分也非必要**。因此「没有白名单就完全上不了岛」在 HyperOS 3 上不成立；缺的是完整焦点模板，不是胶囊位。

---

## 2. 官方白名单是什么

小米把超级岛绑在「焦点通知」上，分两道门：

1. **平台白名单**（开发者向 `mipush-permission@xiaomi.com` 申请）：要应用名、包名、**MiPush appid**、channel、**公司主体**、场景说明与效果图，有效期到当年 12 月 31 日。开通后由平台配置权限，用户打开通知时焦点开关默认一并打开。
2. **用户开关**：`content://miui.statusbar.notification.public` 的 `canShowFocus`。官方示例把它当发送前查询；本仓库只作诊断、不作硬门槛（已在测试中覆盖 `focusPermissionGranted=false` 仍视为 OS3 候选）。

官方还提供客户端直发（通知 extras 写 `miui.focus.param` / `miui.focus.pics`）和 MiPush 下发两条路。本应用无 `INTERNET`、无 MiPush，只能走客户端直发。即便 payload 完全合法，未进平台白名单时，部分 ROM 上 SystemUI 会忽略 extras，只留下标准通知 / Status Chip。

个人开源、无公司主体、无 appid 的应用，按现行流程几乎申请不到。把「申请白名单」当成近期里程碑不现实。

---

## 3. Shizuku 绕过：社区在做什么、为什么这里不做

社区里所谓「Shizuku 绕过超级岛白名单」，不是给本应用申请焦点权限，而是在 **notify 瞬间干扰小米服务框架（`com.xiaomi.xmsf`）**，让云端白名单查询失败并放行本地 `miui.focus` extras。公开实现（HyperCopy、HyperBridge 等）属于同一类手法：用 Shizuku 的 shell 身份调用连通性隐藏接口，短时间隔离 XMSF 再发通知。

这与本仓库的约束对不上：

| 维度 | 现状 | 接入 Shizuku 绕过后 |
|---|---|---|
| 权限模型 | 仅通知 / 精确闹钟 / 开机，用户可理解 | 用户必须装 Shizuku、开无线调试或 Root；重启后要再激活 |
| 隐私政策 | 无网络、无第三方 SDK、不声明 `INTERNET` | 运行时探测系统包、拉起特权进程、改其他 UID 的网络策略 |
| 稳定性 | 标准 Notification API | 依赖 XMSF「不可达则放行」；作者已在 Android 17 / 新系统上承认失效，并视为应被修补的漏洞 |
| 副作用 | 无 | 瞬时切断小米推送 / 账号相关网络；失败时可能漏恢复防火墙规则 |
| 体积与引导 | APK 已压到约 1.3 MB | 依赖 + 设置页向导，服务的是极少数已玩机用户 |
| 合规 | 客户端按官方字段发 extras | 绕过厂商授权，与小米焦点申请声明、应用市场上架审核冲突 |

本应用目标用户是线下行程管理，不是 LSPosed / Shizuku 工具链。为完整大岛模板去要求无线调试，投入与受众不匹配。

**因此：不引入 `rikka.shizuku:api`，不增加「用 Shizuku 开启完整超级岛」开关，不在发通知路径上隔离或探测 XMSF。**

合法替代只有两条，都保持现状即可：

- **HyperOS 3 / Android 16**：继续用 promoted ongoing，胶囊走 Status Chip（已在用）。
- **完整焦点模板**：等官方白名单（产品愿意接 MiPush 之前不推进），或用户在系统设置里若已出现焦点开关则自然生效——现有 payload 已挂上，无需绕过。

---

## 4. 样式适配：已经做了什么，还差什么

官方 OS3 岛是标准模板拼装，不是自定义 RemoteViews。本仓库刻意对齐 HyperIsland-ToolKit 的 `setBigIslandInfo` 图文模板，避免 Kotlin 2.2 第三方二进制。

### 4.1 已对齐（保持）

- `protocol=3`、`updatable=true`、`enableFloat=true`、`islandFirstFloat=true`（首次自动展开，否则 `airtimeCount=0` 永远看不到大岛字）
- 大岛 A 区图标 + 标题，B 区状态文案；**不挂** `sameWidthDigitInfo` / `fixedWidthDigitInfo`（会压缩 A 区，issue #2）
- 小岛 `picInfo` 用 256px 彩色 `ic_island`
- 渠道 `IMPORTANCE_HIGH`（`schedule_live_updates_v2`）
- 常驻胶囊身份靠 `shortCriticalText`（「演·团队名」），倒计时靠 `setWhen()` + chronometer，不靠岛内 digit
- 锁屏 `VISIBILITY_PRIVATE` + 脱敏 publicVersion；岛 / Chip 在解锁表面不受影响

### 4.2 与官方模板的剩余差距（不需要 Shizuku）

官方 Q&A：摘要态左右大约各 4 个字；展开卡片背景只能深色。对照 `XiaomiHyperIslandAdapter.buildJsonParam()`：

| 缺口 | 现状 | 建议 |
|---|---|---|
| A 区字数 | 左标题用完整 `notificationLabel`（「演·空色轨迹」），易超出 4 字 | 岛上 A 区改为团队名截断（约 4 字），类型放到 B 区或只留「后开场 / 已开场」 |
| AOD | 未写 `aodTitle` / `aodPic` | 可补「演·团队」或「下一项」，图标复用 `miui.focus.pic_schedule`；息屏仍受系统策略限制 |
| 岛内按钮 | `miui.focus.actions` 空 Bundle；「完成特典」只在标准 Notification.Action | 进行中特典可把同一 PendingIntent 挂到 `miui.focus.action_complete`，payload `actions` 引用它 |
| `highlightColor` | 未设 | 可选，跟主题色；非上岛条件 |
| `filterWhenNoPermission` | 未设（官方默认 false） | 保持默认：无焦点权限时退化为普通通知，不要丢通知 |
| `islandTimeout` | 未设（官方默认 3600s） | 常驻日程可维持默认；不要用会按分钟误解析的 `timeout` |
| 进度环模板 | 未用官方下载/进度类模板 | **不要换**。日程身份优先于环形进度；Android 16 `ProgressStyle` 已覆盖通知栏进度 |
| `canShowFocus` UI | 设置页无诊断 | 可选：通知设置里只读展示「本机焦点权限」，并说明无权限时仍走 Status Chip |

这些都是改 JSON / extras，不增加依赖、不改隐私模型。优先级建议：

1. **文案长度**（A/B 区按 4 字约束截断）——用户一眼能看出岛是否「像官方」
2. **AOD 标题**——息屏场景补一层，字段小、风险低
3. **岛内完成按钮**——仅进行中特典，与现有 `ACTION_COMPLETE` 共用
4. 诊断文案——方便真机对照，不作门槛

未做白名单时，SystemUI 可能继续忽略 extras；上述改动在「已有焦点权限」或「未来平台开通」时才会完整显现，但 **不会破坏** 现有 Status Chip 路径。

### 4.3 明确不做的样式

- 自定义 `RemoteViews` 状态栏（`miui.focus.rvBar`）：与 Android 16 提升资格冲突（官方 / QPR2 不允许自定义 RemoteViews 的 promoted ongoing）。
- 右侧等宽数字倒计时组件：已用真机否决。
- 每次 refresh 都 `islandFirstFloat=true` 强行展开：首次展开即可；持续展开会打断用户。当前 live 路径是 `enableFloat=true` 且首次展开，保持。
- 为「更像岛」去请求 `setColor()` / colorized：会让 promoted ongoing 失去资格。

---

## 5. 实施边界（给后续改动用）

**可以做**

- 继续只在 `XiaomiHyperIslandCapability.isOs3Supported` 为真时注入 extras。
- 收紧岛上 A/B 文案、补 `aodTitle`、把特典完成接到 `miui.focus.actions`。
- 设置页只读展示 `focusPermissionGranted`，文案写清：无焦点权限时仍显示 Android 16 胶囊。

**不要做**

- Shizuku / Root / LSPosed / HyperCeiler 依赖或引导。
- 运行时改系统防火墙、`appops`、`Settings.Secure` 焦点注册表、停用或断网 XMSF。
- 把 `canShowFocus==false` 改回硬门槛（会让未白名单设备连 payload 都不发，也丢掉未来开通后的自动生效）。
- 接入 MiPush 或声明 `INTERNET` 仅为了交白名单材料。

若产品以后改策略（上架小米应用商店、接受推送 SDK），应先走官方邮件申请，再考虑把焦点权限当作增强而不是前提。

---

## 6. 验证（若只做样式、不做 Shizuku）

保持 [hyperisland-live-updates.md](hyperisland-live-updates.md) 第八节命令，额外看：

```bash
adb shell settings get secure focus_notifs
adb shell dumpsys notification --noredact | grep -A 40 "com.lamuier.scheduletimeline"
```

- `flags` 含 promoted ongoing、extras 含 `android.requestPromotedOngoing=true` → 胶囊路径正常。
- extras 含完整 `miui.focus.param` 且 `canShowFocus=true` → 才预期看到大岛 A/B。
- `canShowFocus=false` 但胶囊仍在 → 符合本评估，不是回归。

单测继续覆盖：无焦点权限仍 `isOs3Supported`；payload 无 digit 计时字段；`islandFirstFloat=true`。
