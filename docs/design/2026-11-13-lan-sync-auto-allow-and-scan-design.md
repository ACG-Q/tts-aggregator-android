# 安卓端局域网同步「自动允许」与「应用内扫码」设计

## 目标

1. 增加可选开关：开启后收到同步请求不再弹窗确认，直接合并。默认关闭、持久化。
2. 局域网同步页右上角增加扫码入口，用应用内相机扫描电脑上的二维码并直接发起同步，替代「必须用系统相机」的路径。

## 非目标

- 不改协议、不改配对串格式、不改 mDNS 行为。
- 不同步 apiKey、禁用状态、分组、失败计数与 cooldown。
- 不做「从相册选图识别」。
- 桌面端不新增扫码（它负责展示二维码）。

## 一、自动允许

### 数据

- `AppSettings` 增加 `lanSyncAutoAllow: Boolean = false`，沿用现有设置持久化。
- `LanSyncViewModel` 构造参数增加 `autoAllow: () -> Boolean`，从 `AppContainer.settings` 实时读取。

### 行为

- `askUser()` 内：`autoAllow()` 为真 → 立即返回 `Accept`，不设置 `pending`，状态文案「已自动允许来自 <设备名> 的同步」。
- 为假 → 现有确认弹窗 + 60 秒超时。
- `X-Sync-Session` 校验仍由 `SyncServer` 先行执行，自动允许不绕过鉴权。

### 界面

局域网同步页「本机配对串」上方新增开关行：

- 标题：`自动允许同步请求`
- 副文案：`开启后不再弹窗确认，仅建议在可信任的网络中使用。`
- 切换即持久化，无需重进页面。

## 二、应用内扫码

### 依赖

- `androidx.camera:camera-camera2`、`camera-lifecycle`、`camera-view`（1.3.4，Apache-2.0）
- `com.google.mlkit:barcode-scanning:17.3.0`（bundled 模型，不依赖 GMS，Apache-2.0）
- 预计 APK 增加 3~4 MB。

### 权限

- `AndroidManifest.xml` 增加 `android.permission.CAMERA`。
- `<uses-feature android:name="android.hardware.camera" android:required="false" />`，无相机设备仍可安装并使用粘贴配对串。

### 入口

`LanSyncScreen` 的 `PulseSubHeader(actions = …)` 放扫码图标按钮（`Icons.Outlined.QrCodeScanner`），点击打开全屏扫码层。

### 扫码界面

- 全屏半透明覆盖：CameraX 预览、中央取景框、提示「对准电脑上的二维码」。
- 顶部关闭按钮，右下角手电筒开关。
- 不新增导航页面栈，作为 `LanSyncScreen` 内的覆盖层状态，返回键先关闭扫码层。

### 识别与结果

1. ML Kit 识别到二维码文本：
   - `parsePairingString` 成功 → 关闭扫码层、回填输入框、调用 `syncFromString` 立即同步。
   - 解析失败 → 显示「不是 Pulse 配对码」，保持扫码状态；同一文本 1.5 秒内不重复提示。
2. 相机权限：
   - 首次点击入口时请求；拒绝或永久拒绝 → 显示「需要相机权限，可在系统设置中开启；也可以继续粘贴配对串」。
3. 生命周期：
   - 扫码层关闭或页面销毁时解绑 CameraX、关闭 ML Kit scanner、释放手电筒。

## 三、顺带修复（已在工作区）

- `MainActivity` 增加 `android:launchMode="singleTask"`：修复应用在前台时深链无效、并叠出第二个 Activity。
- `SyncClient.readTimeout` 由 15 秒改为 70 秒：覆盖对端 60 秒确认窗口，避免用户稍晚确认时手机先报连接失败。

## 测试

- 单测：`parsePairingString` 对扫码文本的解析（已有用例覆盖，补充带空白的边界）。
- 单测：自动允许开关默认值与序列化（`AppSettings`）。
- 手动验收（模拟器 + 桌面）：
  1. 自动允许开启：手机发起 → 桌面无弹窗、合并成功；桌面发起 → 手机无弹窗。
  2. 自动允许关闭：拒绝、60 秒超时（408）提示正确。
  3. 扫码：扫桌面二维码 → 直达同步；扫无关二维码 → 提示后继续扫。
  4. 拒绝相机权限 → 提示且粘贴路径可用。
  5. 退出同步页 → 相机与端口均释放。

## 验收标准

1. 开关默认关闭，重启后保持；两端行为对称。
2. 扫码入口位于同步页右上角，扫到配对码后直接发起同步。
3. `testDebugUnitTest`、`assembleDebug` 通过。
