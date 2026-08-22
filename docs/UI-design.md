# UI 设计规范

面向「追程」Compose UI 的约定。改界面前先对照本文与 `ui/theme/Theme.kt`；工程分层见 [`AGENTS.md`](../AGENTS.md)。

---

## 1. 设计原则

1. **时间优先**：主屏以当日时间轴为核心；次要操作（主题、导入导出、清空）收进菜单 / 底栏，不抢主视觉。
2. **一眼可扫**：日程块用分类色区分；空闲与冲突用固定语义色，不靠文案才能辨认。
3. **轻量本地**：无账户、无云同步 UI；避免「仪表盘感」——不要堆统计卡片、多 Tab 首页。
4. **Material 3 为本**：组件优先用 M3；时间轴线与卡片色保持与主题色一致。
5. **深浅双主题**：浅色 / 深色 / 跟随系统均可用；分类色在深色下用 `adaptTo(dark)`，禁止写死仅浅色可读的对比。

---

## 2. 色彩

定义位置：`app/.../ui/theme/Theme.kt`。新增语义色放此处，勿在 Composable 里散落魔法色值（图表/分类算法内哈希取色除外）。

### 品牌与语义

| Token | 色值（浅色） | 用途 |
|---|---|---|
| `PurpleTime` / `primary` | `#7C5CFC` | 主色、时间强调、主按钮 |
| `OrangeGap` / `secondary` | `#F59E0B` | 空闲时段 |
| `RedLocation` / `error` | `#EF4444` | 地点强调、错误、冲突相关 |
| `WarningAmber` | `#F59E0B` | 警告条（与空闲同色系时可复用 secondary） |

### 表面（浅色）

| Token | 色值 | 用途 |
|---|---|---|
| `background` | `#F6F5FA` | 页面底 |
| `surface` | `#FFFFFF` | 卡片、顶栏 |
| `surfaceVariant` | `#EDECF4` | 弱分区、芯片底 |
| `onSurface` | `#1B1A21` | 主文字 |
| `onSurfaceVariant` | `#5F5C6B` | 次文字 |
| `outlineVariant` | `#E3E1EC` | 分割线、描边 |

深色方案以 `DarkColors` 为准；状态栏 / 导航栏外观随 `LocalDarkTheme` 切换。

### 分类色

- 使用 `categoryColors(name)` + `adaptTo(dark)`。
- 常见分类有固定索引（舞台演出、特典、物贩等）；其余按名称哈希稳定取色。
- 日程块：`container` 为底、`onContainer` 为字、`accent` 为左边条 / 强调点。
- **不要**为单个分类在 UI 文件里单独硬编码颜色。

---

## 3. 字体

沿用 `ScheduleTimelineTheme` 中覆盖的 Typography（SansSerif）：

| 样式 | 字重 | 字号 | 典型用途 |
|---|---|---|---|
| `titleLarge` | SemiBold | 22.sp | 顶栏标题、空状态主文案 |
| `titleMedium` | Medium | 17.sp | 日程标题、分区标题 |
| `bodyLarge` | Normal | 16.sp | 表单正文、列表主行 |
| `bodyMedium` | Normal | 14.sp | 次要说明、地点、时长 |
| `labelLarge` | Medium | 15.sp | 按钮、芯片、表单项标签 |

规则：

- 优先 `MaterialTheme.typography.*`，不要随意 `TextStyle(fontSize = …)`。
- 用户可见文案进 `strings.xml`；`contentDescription` 同样走 string 资源。

---

## 4. 间距与圆角

常用刻度（与现有界面一致，新增 UI 尽量复用）：

| 用途 | 建议 |
|---|---|
| 页面水平边距 | 16–24.dp（编辑页 24.dp；列表内容约 12–20.dp） |
| 卡片内边距 | 16.dp |
| 表单项纵向间距 | 16–20.dp；组内 8–12.dp |
| 芯片 / 小标签内边距 | 水平 8–12.dp，垂直 2–8.dp |
| FAB / 底栏避让 | 列表底部留白约 80.dp |
| 卡片圆角 | 16.dp |
| 输入框 / 主按钮圆角 | 12.dp |
| 分类芯片圆角 | 8–10.dp |

禁止无理由使用过大圆角（如全面 `RoundedCornerShape(50)` 胶囊堆叠）或多层阴影炫技。

---

## 5. 组件与交互

### 主屏（`ui/timeline/`）

| 元素 | 约定 |
|---|---|
| 顶栏 | 「追程」标题在左上；日期导航在标题下方居中，可点选；左右箭头切日；右侧菜单 |
| 视图 | 仅列表（`TimelineList`）；左右拖动时内容跟手位移，松手后吸附返回或完成切日 |
| 卡片 | 概览模式：主标题为团队集合、标签为演出或前特/平特/终特；地点、关联、冲突提示与备注收入点击后的底部详情 Sheet，不占卡片主区；跨类型重叠时左列固定演出、右列固定特典，同类事件在各自列内纵向排列；并行轨道相邻卡片留 4.dp 间隙便于辨识 |
| FAB | 右下为「添加日程」；非今天时左下显示「返回今天」；破坏性操作不放 FAB |
| 菜单 | 主题、导入、导出、清空等次要入口 |
| 数据管理 | 底栏 / Sheet（`ManageDataSheet`），分组清晰，清空需二次确认 |
| 通知 | 设置页提供总开关与「显示未来日程」子开关；Android 13+ 请求权限；空档期展示下一项倒计时 |

### 编辑页（`ui/edit/`）

- `Scaffold` + `CenterAlignedTopAppBar`；返回用 `Icons.AutoMirrored.Filled.ArrowBack`。
- 表单状态走 `EditUiState` + ViewModel，不在 Composable 里直接写库。
- 删除日程 / 删除分类用 `AlertDialog` 确认。
- 时间用系统时间选择器；改开始时间尽量保持原时长（已有逻辑勿破坏）。
- 演出团队单选；特典团队多选；不提供手工绑定演出 / 特典的控件。

### 反馈

- 校验错误：枚举 / `EditValidationError` + `strings.xml`，在字段旁或表单区展示。
- 不可恢复操作（清空全部）：对话框确认，文案明确后果。
- 避免无意义全屏 Loading；本地操作应瞬时完成。

### 图标

- **只使用** `material-icons-core`（及已有的 AutoMirrored）。
- 缺图标时用 core 内语义接近的替代，**禁止**引入 `material-icons-extended`。

---

## 6. 时间轴视觉语义

| 元素 | 表现 |
|---|---|
| 日程事件 | 分类色容器；列表卡片 |
| 空闲 | `OrangeGap` / secondary；文案带时长 |
| 重叠冲突 | 警告色 + 简短提示（Warning 图标可用 core 的 `Warning`） |
| 切日 | 左右拖动主内容时实时跟手；越过阈值完成切换，否则回弹；箭头和 DatePicker 仍保留方向过渡 |
| 重叠 | 演出与特典跨类型重叠时，时间轴节点下固定双线：演出一线、特典一线；同类多项不跨线 |
| 当前时间线 | 今天页左侧时间轴留白区（时间标签右缘 ~ 卡片左缘）一条红色短线 + 圆点，标出「现在」所在位置；不进入卡片内容区，位置由各 item 实际布局测量得出。进入今天且当前落在某段日程/空闲时，列表把该时间线滚到视口垂直居中 |
| 进行中日程 | 取消卡片左侧红进度条；改为日程边框闪烁。当前时刻落入重叠窗口时，冲突的各方都闪 |

算法在 `TimelineBuilder` / `ChartLayout`（统计条仍用 `ChartLayout.stats`）；UI 只负责绑定结果。

---

## 7. 布局与文件拆分

```
ui/
├── theme/          Theme、色板、LocalDarkTheme
├── timeline/       主屏、列表、数据管理 Sheet
└── edit/           编辑页与 EditUiState（状态类可同包）
```

- 新增大块界面**拆新文件**，避免继续膨胀 `TimelineScreen`。
- 关键 Composable（空状态、统计条、事件卡、编辑页等）尽量带 `@Preview`。
- 不在 Composable 中访问 Database / DAO。

---

## 8. 无障碍与文案

- 可点击图标必须有 `contentDescription`（装饰性图标可为 `null`）。
- 对比度：主文字 / 按钮文字在浅色与深色下均需可读；分类 `onContainer` 勿随意改浅。
- 中文文案统一简体；语气简洁、操作结果可预期（「清空全部数据」而非含糊的「重置」）。

---

## 9. 改 UI 时的检查清单

- [ ] 颜色来自 `Theme.kt` / `MaterialTheme.colorScheme` / `categoryColors`
- [ ] 深色模式目视过一遍（或 Preview 双主题）
- [ ] 文案与 contentDescription 在 `strings.xml`
- [ ] 未引入 `material-icons-extended`
- [ ] 关键块有 `@Preview`；大块 UI 已拆文件
- [ ] 破坏性操作有确认
- [ ] 用户可见行为变更已更新 `README.md` / `CHANGELOG.md`「未归档」
