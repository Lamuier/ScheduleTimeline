# 技术参考（Technical）

本文档收录偏工程向的内容：构建方式、CSV 格式、版本规则、技术栈与项目结构。面向使用者/功能介绍的内容见根目录 [README.md](../README.md)。

## 构建 APK

Windows 使用 `build.ps1`，macOS / Linux 使用 `build.sh`；两者交给标准库 Python 3.9+ 的 `scripts/build_support.py` 执行相同流程。需要完整且仍维护的 JDK 17+ 修订版、Android SDK 37 与 Build Tools 37.0.0：

```powershell
.\build.ps1                 # Debug
.\build.ps1 -Install        # Debug + adb 安装（独立包名，不覆盖 Release）
.\build.ps1 -Release        # Release 签名打包到 dist/
.\build.ps1 -SetupSigning   # 显式绑定已有原签名
.\build.ps1 -Help           # 参数说明（也支持 --help）
```

启动时会打印「生效参数」（模式、Clean / SkipChecks / Gradle 离线是否生效等）；Gradle 实际命令行也会写出。

Debug 与 Release 均默认离线，可用 `-Online` / `--online` 下载缺失依赖。macOS / Linux 运行 `bash build.sh`（Debug）、`bash build.sh --release`（正式包）、`bash build.sh --release --online` 或 `bash build.sh --release --doctor`（只预检、不下载）。

可用 `-JavaHome` / `--java-home` 指定维护中的 JDK。本机早期 OpenJDK 17 GA 在 Lint 的 C2 编译阶段复现过 JVM 崩溃，验收改用官方 SHA-256 验证过的 Temurin 17 修订版；这不改变应用 JVM 目标 17。

默认会跑单测与 Debug Lint；Release 另跑 Release Vital Lint；加 `-SkipChecks` 可跳过。需要干净构建时加 `-Clean`。

| 产物 | 路径 | applicationId |
| --- | --- | --- |
| Debug | `app\build\outputs\apk\debug\app-debug.apk` | `com.lamuier.scheduletimeline.debug` |
| Release | `dist\ScheduleTimeline-vX.Y.Z-release.apk` | `com.lamuier.scheduletimeline` |

真机调试必须安装 Debug 包（`.debug` 后缀），与本机正式版并存；禁止对正式包名 `adb uninstall` 再装 Debug，以免清空用户数据。

### Release 签名

密钥与密码不进仓库。默认本机元数据在被忽略的 `.signing/`（可用 `-SigningRoot` / `--signing-root` 指定）。未配置时 Release 会停止；必须沿用原密钥，并校验证书固定指纹及实际私钥签名操作。不会自动绑定 Debug 签名或生成新密钥。

使用已有 UTF-8 单行凭据文件（保留密码本身的空格，只去掉末尾换行；限制为本机用户可读），项目元数据只引用其路径：

```powershell
.\build.ps1 -SetupSigning `
  -AdoptKeystore "C:\path\to\release.keystore" `
  -KeyAlias "release" `
  -CredentialFile "C:\private\release-password.txt" `
  -CertificateSha256 "<原证书 SHA-256>"

.\build.ps1 -Release -Online
```

旧 `-StorePassword`、`-GenerateNewKey`、`-AdoptDebugKeystore`、`-ForceRebind` 参数会给出迁移提示并停止；Windows DPAPI / `.clixml` 密码容器不可跨系统使用，改用同一原密码的私有文件。原有 `-Install / -Release / -Clean / -Offline / -Online / -SkipChecks / -SigningRoot / -Help` 开关继续支持。

macOS / Linux 绑定命令：

```bash
bash build.sh --setup-signing --adopt-keystore /private/path/release.keystore \
  --key-alias release --credential-file /private/path/release-password.txt \
  --certificate-sha256 <原证书SHA256>
```

入口会校验 APK 原签名、包名、版本、SDK、ABI、非 debuggable、安全默认值、ZIP/ELF 16 KiB 对齐、CRC 和非空 R8 mapping。通过后才发布到 dist；失败保留已有产物，并导出 SHA-256 与 build.json。共享逻辑测试使用 `python3 -m unittest discover -s scripts/tests -v`。

`build.ps1 -Release` / `build.sh --release` 会注入 `ANDROID_RELEASE_*` 环境变量；`app/build.gradle.kts` 据此配置 `signingConfigs.release`。相关环境变量：

```text
ANDROID_RELEASE_STORE_FILE
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

Gradle Wrapper 与 Maven 依赖优先使用华为云国内镜像；Gradle 9.4.1 分发包固定官方 SHA-256 校验值，官方仓库保留为依赖回退源。

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
日期, 团队（多个用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始时间, 结束时间, 备注, 完成
2026-06-01, StarDiary, 演出, , , 主舞台, 14:20, 14:40, ,
2026-06-01, StarDiary / 银烁花火, 特典, 平特, , 吧台A, 17:00, 19:00, , 是
```

- 特典可填写多个团队，使用 `/`、`、` 或 `|` 分隔；同日按团队名称与演出自动关联。
- 导入或保存后，同一天里起止时间、类型、特典种类都相同的日程会自动合并成一条，团队名合并，地点 / 场次说明 / 备注保留不同内容。
- 类型：`演出` / `特典`；特典种类：`前特` / `平特` / `终特`（演出留空）。
- 「完成」列：已完成特典导出为 `是`；演出恒为空。导入时仅特典认该标记。
- 导入页长按样例会复制**含表头**的示例；解析时自动跳过标题行。
- 兼容旧 9 列格式（无完成列）。
- 兼容旧 10 列格式；原「关联演出开始时间」列（时钟值）会被忽略。
- 兼容旧 6–7 列格式（标题 → 团队，分类 → 类型推断）。
- `ImportParseResult` 返回有效草稿（带原始行号）与错误枚举；有效时间为 00:00–23:59，结束必须晚于开始。未知结束留空并提示，不引入跨午夜或结束待定语义。
- 合并对重复导入保持幂等；不会清洗历史备注。保存、导入、清空均先完成事务再关闭页面；特典完成只更新该字段。
- 旧异常时间记录保留可编辑入口，但排除比例、统计和提醒。

「清空全部数据」不可恢复。

### 通知权限

在主屏右上角「更多 → 通知」开启。Android 13 及以上会请求通知权限；日程通知须经全局开关与 `POST_NOTIFICATIONS` 权限。关键时间点提醒跟随总开关（与「显示未来日程」开关无关）。Live Updates / 超级岛的适配细节见 [hyperisland-live-updates.md](hyperisland-live-updates.md)。

## 技术栈

Kotlin · Jetpack Compose BOM 2026.08.00 / Material 3 1.4.0 · MVVM（ViewModel + StateFlow + Repository） · Room

- 最低 API 26 · 编译 API 37 · 目标 API 37（Android 17）
- ABI 仅 `arm64-v8a`
- 包名 `com.lamuier.scheduletimeline`
- 固定 `buildToolsVersion = "37.0.0"`（与 compileSdk 37 对齐，保证 Release 可复现）
- AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.10（AGP 9 内置 Kotlin，无需 `kotlin-android` 插件；JVM 目标 17）

## 项目结构

```text
ScheduleTimeline/
├── build.ps1 / build.sh          ← 跨平台入口
├── scripts/build_support.py      ← 环境、签名、检查、导出
├── build-policy.json             ← ABI / 权限 / Release 内容校验
├── CHANGELOG.md
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

## 时间、布局与调度

`ScheduleTimeSnapshot` 使用可注入 Clock 与时区，前台恢复立即刷新，跨午夜只更新今天身份。分页以日期作为稳定标识，远日期重新设置中心；编辑会话按导航条目绑定，草稿与所属日期经 SavedStateHandle 同步保存。

`TimelineLayoutPlan` 输出统一切片、高度与时间到纵坐标的映射；所有重叠簇进入切片，首尾相接分开。同类型全宽，混合类型双列。Compose 使用文字实测高度和宽度，卡片与红线共用映射，标签按卡片实测高度定位。

提醒使用持久化 `ReminderState` 与 `ReminderBatch`，刷新串行执行，广播处理前重新核对数据库。已安排的延迟提醒仅在未开场时补发并按事件合并；新录入不补历史提醒。事件改期、删除、完成及关闭通知会清理旧提醒。日期与时刻组合后经 `atZone` 转时间戳，系统时间、时区、重启与更新后重算。精确闹钟权限不可用时回退非精确闹钟，送达时间仍受系统限制，参见 [Android 闹钟文档](https://developer.android.com/develop/background-work/services/alarms)。

有已放置小组件时，关闭通知仍安排事件边界和跨日刷新；两者都无需求则停止。列表以事件 ID 作为稳定 ID，不同通知、小组件及完成动作使用独立 Intent 身份。

回归验收包括 JVM 算法、Room 事务失败、SavedState 草稿恢复，以及 Robolectric/Compose 的 320 / 360 / 600dp、100% / 150% / 200% 字体与深浅主题矩阵。默认不用模拟器与真机；Windows/Linux 分支通过脚本单测，实机构建结果以实际运行平台为准。
