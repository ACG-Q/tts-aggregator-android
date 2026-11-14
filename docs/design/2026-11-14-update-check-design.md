# 检查更新与强制更新设计

## 背景

当前只能通过 GitHub Release 手动下载 APK。zuoban/tts 若发生协议级变更，旧客户端可能无法继续合成，
需要在发布侧有能力要求旧版本必须升级，同时不打扰普通版本用户。

目标：

- 默认不自动提示普通新版本，检查更新由用户手动触发
- 预留「强制更新」通道：服务端可要求低于最低支持版本的客户端升级
- 支持 GitHub 加速地址，解决直连 GitHub 不稳定的问题
- 检查或下载失败都不能阻断应用正常使用

## 更新源

客户端固定请求：

```text
https://github.com/acg-q/tts-aggregator-android/releases/latest/download/update.json
```

`update.json` 由 CI 在发布时生成并作为 Release 资产上传，`releases/latest` 会自动指向最新正式 Release，
地址因此保持不变。

### update.json

```json
{
  "versionName": "1.0.5",
  "versionCode": 10005,
  "minSupportedVersionCode": 10000,
  "releasePage": "https://github.com/acg-q/tts-aggregator-android/releases/tag/v1.0.5",
  "notes": "本次更新内容",
  "apks": {
    "arm64-v8a": "https://github.com/.../Pulse-v1.0.5-arm64-v8a.apk",
    "universal": "https://github.com/.../Pulse-v1.0.5-universal.apk"
  },
  "sha256": {
    "arm64-v8a": "…",
    "universal": "…"
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `versionName` | 展示用版本号 |
| `versionCode` | 最新版本的 versionCode，判定是否可选更新 |
| `minSupportedVersionCode` | 最低支持版本，低于它必须强制更新；`0` 表示不强制 |
| `releasePage` | 下载失败时的浏览器回退地址 |
| `notes` | 更新说明，纯文本 |
| `apks` | ABI → APK 下载地址 |
| `sha256` | ABI → APK 的 SHA-256，用于下载校验 |

解析使用 `ignoreUnknownKeys = true`，字段缺失或类型错误都视为无效清单，不弹提示。

### update-policy.json

仓库根目录维护一份发布侧开关：

```json
{
  "minSupportedVersionCode": 0
}
```

平时保持 `0`。当 zuoban/tts 出现破坏性变更时，在发新版前把它改成「必须升级到的新版本 versionCode」，
CI 会写入 `update.json`。强制更新因此是发布侧操作，不需要发布客户端代码。

## 版本判定

一律比较 `versionCode`（`major * 10000 + minor * 100 + patch`），不比较版本号字符串。

```text
current >= info.versionCode             → 已是最新
current <  info.minSupportedVersionCode → 强制更新
current <  info.versionCode             → 可选更新
```

`minSupportedVersionCode` 小于当前版本时，即使清单异常也不会触发强制更新。

## 检查时机

| 场景 | 行为 |
| --- | --- |
| 冷启动（非旋转重建） | 静默请求一次，6 小时内不重复；仅强制更新弹窗 |
| 设置页 / 关于页点「检查更新」 | 绕过节流，明确反馈结果 |
| 网络失败 / 404 / JSON 异常 | 启动时静默忽略；手动时提示检查失败 |

不引入 WorkManager，不做后台轮询，不主动提示普通新版本。

## GitHub 加速地址

设置项「GitHub 加速地址」保存在 `AppSettings.githubProxy`，默认空字符串表示直连。

- 输入必须是 `http://` 或 `https://` 开头，保存时自动补尾部 `/`
- 拼接方式：`加速地址 + 原 GitHub URL`，例如
  `https://ghproxy.net/https://github.com/.../update.json`
- 仅作用于检查更新与 APK 下载；Release 页面不使用代理
- 代理不参与签名校验：SHA-256 与清单来自同一来源，安全性依赖用户对代理的信任

失败回退顺序：

| 请求 | 顺序 |
| --- | --- |
| 手动检查更新 | 代理 → 直连 → 提示失败 |
| 启动静默检查 | 代理 → 直连 → 静默放弃 |
| APK 下载 | 代理 → 直连 → 浏览器打开 Release 页 |

## 交互

### 设置页

新增「应用」分区：

- 「检查更新」：副标题显示当前版本，检查中右侧显示「检查中…」
- 「GitHub 加速地址」：右侧显示当前地址（留空显示「直连」），点击弹出输入框

### 关于页

版本号下方增加「检查更新」文本按钮，与设置页共用同一套检查逻辑与弹窗。

### 弹窗

- **可选更新**：标题「发现新版本 v1.0.5」，正文为更新说明（限高可滚动），按钮「立即更新 / 稍后」
- **强制更新**：不可关闭（返回键、点击外部、取消均无效），只有「立即更新」和「退出应用」，说明旧版本与 TTS 服务端不再兼容
- **下载进度**：进度条 + 取消，未知文件大小时使用不确定进度

下载完成后校验 SHA-256，再拉起系统安装器。未授予「安装未知应用」权限时跳转本应用的对应设置页；
下载或校验失败时打开 `releasePage` 由浏览器兜底。

## 代码结构

```text
data/UpdateInfo.kt        清单 DTO、判定/代理/ABI/节流等纯逻辑
data/UpdateClient.kt      拉取 update.json（10 秒超时）
data/UpdateDownloader.kt  流式下载、进度回调、SHA-256 校验
data/UpdateRepository.kt  状态、节流、代理回退、下载编排
ui/screens/UpdateDialogs.kt  可选/强制/下载进度弹窗与全局宿主
ui/util/ApkInstaller.kt   安装未知应用权限与系统安装器
viewmodel/UpdateViewModel.kt 检查、下载、取消的入口
```

新增依赖：无（OkHttp、kotlinx.serialization 已存在）。

新增权限与配置：

- `REQUEST_INSTALL_PACKAGES`
- `FileProvider`（只暴露 `cache/updates/`）

## CI

`release.yml` 在生成 Release 说明后：

1. 读取 `update-policy.json` 的 `minSupportedVersionCode`
2. 从 APK 的 `.sha256` 文件收集校验值
3. 生成 `update.json` 并校验 `minSupportedVersionCode <= versionCode`
4. 与 APK 一起上传到 Release

## 边界说明

- 强制更新只拦截应用界面，不主动停止系统 TTS 服务
- 安装包下载到应用缓存目录，安装后系统会自行清理旧缓存
- 版本号随本次功能升到 1.0.5，由 `scripts/release.ps1` 处理
