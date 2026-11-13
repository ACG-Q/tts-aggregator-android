# 安卓按 ABI 拆分 APK 设计

日期：2026-11-13

## 背景

当前每次发布只产出一个通用 APK（v1.0.2 为 33.1 MB）。应用引入 CameraX 与 bundled ML Kit 后包含原生库，通用包会把四个 ABI 的原生库全部打包，实际设备只需要其中一份。目标是在保留通用包兜底的前提下，按 ABI 分别出包以减小下载体积。

ML Kit 与 CameraX 的原生库只覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，因此不引入 `mips` / `riscv64` 等在依赖上缺失的 ABI。

## 方案

采用 Gradle 的 ABI splits，一次 `assembleRelease` 产出 4 个分架构包与 1 个通用包；不使用 AAB + bundletool，也不引入 productFlavors。

## 构建配置

`app/build.gradle.kts` 的 `android` 块内增加：

```kotlin
splits {
    abi {
        isEnable = true
        reset()
        include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        isUniversalApk = true
    }
}
```

- 对 debug 与 release 均生效。
- 各分发包与通用包 `versionCode` 保持一致。发布渠道是 GitHub 直发 APK，不涉及 Play 的 versionCode 唯一性要求。
- 通用包包含全部四个 ABI，作为不确定架构时的兜底。

## 发布流水线

`.github/workflows/release.yml` 调整：

1. 构建完成后统计 `app/build/outputs/apk/release/*.apk`，必须恰好 5 个，否则失败，避免 Gradle 行为变化导致漏包。
2. 对每个 APK 执行 `apksigner verify --print-certs`。
3. 按 ABI 重命名：
   - `Pulse-v{版本}-arm64-v8a.apk`
   - `Pulse-v{版本}-armeabi-v7a.apk`
   - `Pulse-v{版本}-x86.apk`
   - `Pulse-v{版本}-x86_64.apk`
   - `Pulse-v{版本}-universal.apk`
4. 每个 APK 生成同名 `.sha256`。
5. 全部 APK 与校验文件上传 artifact，并作为 Release 附件。
6. 发布说明的「下载」表逐行列出 ABI、文件大小与 SHA-256，并注明：大多数手机选 `arm64-v8a`，不确定架构时用 `universal`。

## 发版脚本

`scripts/release.ps1` 不变，版本号仍只维护 `app/build.gradle.kts` 中的 `appVersionName`。

## 文档

README 补充发布产物与本地构建说明：

- Release 提供四个 ABI 包与通用包。
- 本地 `assembleDebug` 同样按 ABI 拆分，模拟器安装使用 `app-x86_64-debug.apk`。

## 测试与验证

- 本地执行 `assembleRelease`，确认输出目录出现 5 个 APK 且文件名符合预期。
- 现有单元测试不受影响，`testDebugUnitTest` 保持通过。
- CI 中以 APK 数量与签名校验作为发布前的自动验证。

## 非目标

- 不引入 AAB / bundletool。
- 不改变版本号规则与发版流程。
- 不为缺失原生库的 ABI 出包。
