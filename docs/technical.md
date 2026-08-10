# 技术参考（Technical）

本文档收录偏工程向的内容：构建方式、CSV 格式、版本规则、技术栈与项目结构。面向使用者/功能介绍的内容见根目录 [README.md](../README.md)。

## 构建 APK

统一入口为 `build.ps1`：

```powershell
.\build.ps1                 # Debug
.\build.ps1 -Install        # Debug + adb 安装（独立包名，不覆盖 Release）
.\build.ps1 -Release        # Release 签名打包到 dist/
.\build.ps1 -SetupSigning   # 配置/重绑签名
.\build.ps1 -Help           # 参数说明（也支持 --help）
```

启动时会打印「生效参数」（模式、Clean / SkipChecks / Gradle 离线是否生效等）；Gradle 实际命令行也会写出。

默认会跑单测与 lint；加 `-SkipChecks` 可跳过。需要干净构建时加 `-Clean`。

| 产物 | 路径 | applicationId |
| --- | --- | --- |
| Debug | `app\build\outputs\apk\debug\app-debug.apk` | `com.lamuier.scheduletimeline.debug` |
| Release | `dist\ScheduleTimeline-vX.Y.Z-release.apk` | `com.lamuier.scheduletimeline` |

真机调试必须安装 Debug 包（`.debug` 后缀），与本机正式版并存；禁止对正式包名 `adb uninstall` 再装 Debug，以免清空用户数据。

### Release 签名

密钥不进仓库。签名元数据在 `%LOCALAPPDATA%\ScheduleTimeline\signing\`。首次 `-Release` 若尚未配置，会自动绑定本机 `debug.keystore`（仅便于本机安装）。上架前请绑定正式密钥：

```powershell
.\build.ps1 -SetupSigning `
  -AdoptKeystore "C:\path\to\release.keystore" `
  -KeyAlias "release" `
  -StorePassword "<pass>"

.\build.ps1 -Release -Online
```

`build.ps1 -Release` 会注入 `ANDROID_RELEASE_*` 环境变量；`app/build.gradle.kts` 据此配置 `signingConfigs.release`。相关环境变量：

```text
ANDROID_RELEASE_STORE_FILE
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

Gradle Wrapper 与 Maven 依赖优先使用华为云国内镜像；Gradle 8.13 分发包固定官方 SHA-256 校验值，官方仓库保留为依赖回退源。

### 版本号（X.Y.Z）

`app/build.gradle.kts`：

```kotlin
versionCode = N      // 整数，上架递增
versionName = "X.Y.Z"
```

- Debug：`versionName` 自动加 `-debug` 后缀（如 `1.8.1-debug`），且 `applicationIdSuffix = ".debug"`。
- Release：无后缀（如 `1.8.1`），包名为正式 `applicationId`。

版本级别判定：不向后兼容的接口/数据/权限变更升主版本（X）；向后兼容地新增用户可见能力升次版本（Y）；仅修复与维护升修订版（Z）。发版须先写明「版本判定：当前 → 目标（依据）」。

## CSV 导入格式

```text
日期, 团队（多个用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始时间, 结束时间, 备注
2026-06-01, StarDiary, 演出, , , 主舞台, 14:20, 14:40,
2026-06-01, StarDiary / 银烁花火, 特典, 平特, , 吧台A, 17:00, 19:00,
```

- 特典可填写多个团队，使用 `/`、`、` 或 `|` 分隔；同日按团队名称与演出自动关联。
- 类型：`演出` / `特典`；特典种类：`前特` / `平特` / `终特`（演出留空）。
- 导入页长按样例会复制**含表头**的示例；解析时自动跳过标题行。
- 兼容旧 10 列格式；原「关联演出开始时间」列会被忽略。
- 兼容旧 7 列格式（标题 → 团队，分类 → 类型推断）。

「清空全部数据」不可恢复。

### 通知权限

在主屏右上角「更多 → 通知」开启。Android 13 及以上会请求通知权限；日程通知须经全局开关与 `POST_NOTIFICATIONS` 权限。关键时间点提醒跟随总开关（与「显示未来日程」开关无关）。Live Updates / 超级岛的适配细节见 [hyperisland-live-updates.md](hyperisland-live-updates.md)。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM（ViewModel + StateFlow + Repository） · Room

- 最低 API 26 · 编译 API 36.1 · 目标 API 36
- ABI 仅 `arm64-v8a`
- 包名 `com.lamuier.scheduletimeline`
- 固定 `buildToolsVersion = "36.1.0"`（与 compileSdk 36.1 对齐；本机缺 AGP 默认 35.0.0 故显式固定，保证 Release 可复现）

## 项目结构

```text
ScheduleTimeline/
├── build.ps1                     ← 唯一构建入口
├── AGENTS.md / CHANGELOG.md
├── docs/
│   ├── UI-design.md              ← UI 设计规范
│   └── hyperisland-live-updates.md  ← Live Updates / 超级岛适配
├── app/
│   ├── build.gradle.kts          ← 版本号、依赖、签名
│   ├── schemas/                  ← Room schema
│   └── src/main/java/.../
│       ├── ScheduleApplication.kt
│       ├── ScheduleViewModel.kt
│       ├── data/                 ← Room、时间轴算法、导入导出
│       └── ui/timeline|edit|theme/
└── app/src/test/                 ← TimelineBuilder / ChartLayout / ScheduleExport
```
