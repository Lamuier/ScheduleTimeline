# 第三方组件声明 · THIRD_PARTY_NOTICES

追程 ScheduleTimeline 使用了以下开源组件。构建打包时各依赖内嵌的重复许可文本（`META-INF/**/LICENSE.txt`、`NOTICE.txt`）已被去重排除，本文件作为集中声明替代。

## 运行时依赖（打包进 APK）

均来自 Android Open Source Project，以 **Apache License 2.0** 授权，版权所有 © The Android Open Source Project。

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| androidx.core:core-ktx | 1.15.0 | 核心工具库 |
| androidx.lifecycle（runtime / runtime-compose / viewmodel-compose） | 2.8.7 | 生命周期与 ViewModel |
| androidx.activity:activity-compose | 1.9.3 | Compose 宿主 Activity |
| androidx.compose（BOM 2024.12.01：ui、ui-graphics、material3、material-icons-core） | — | UI 框架与 Material 3 |
| androidx.navigation:navigation-compose | 2.8.5 | 页面导航 |
| androidx.room（runtime / ktx，编译期 KSP 生成代码） | 2.8.4 | 本地数据库 |
| org.jetbrains.kotlin:kotlin-stdlib（Kotlin 2.3.10） | — | Kotlin 标准库，版权所有 © JetBrains s.r.o. |

Apache License 2.0 全文：<https://www.apache.org/licenses/LICENSE-2.0>

## 测试与构建期依赖（不打包进 APK）

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| junit:junit | 4.13.2 | Eclipse Public License 1.0，© JUnit contributors |
| org.json:json | 20240303 | JSON.org License（公有领域风格），© JSON.org |
| com.android.tools.build:gradle（AGP） | 9.2.1 | Apache License 2.0 |
| com.google.devtools.ksp | 2.3.10 | Apache License 2.0 |
| org.gradle.toolchains.foojay-resolver-convention | 0.10.0 | Apache License 2.0 |
| Gradle | 9.4.1 | Apache License 2.0，© Gradle Inc. |

## 说明

- 本项目自身以 [MIT License](LICENSE) 开源。
- 以上清单随依赖升级维护；如发现遗漏，欢迎通过仓库 Issue 指出。
