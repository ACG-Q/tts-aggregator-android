# 安卓端局域网同步「自动允许 + 应用内扫码」实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 安卓同步页新增「自动允许」开关（默认关闭、持久化）与右上角扫码入口（CameraX + ML Kit），扫到配对码后直接发起同步。

**架构：** 开关进 `AppSettings`，`LanSyncViewModel` 通过回调读取；扫码封装成独立 Composable 覆盖层，识别结果复用 `syncFromString`。

**技术栈：** Kotlin + Jetpack Compose + CameraX 1.3.4 + ML Kit Barcode Scanning 17.3.0。

---

## 文件结构

- 修改 `app/src/main/java/com/pulse/tts/data/AppSettings.kt`：新增 `lanSyncAutoAllow`。
- 修改 `app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt`：注入 `autoAllow`，`askUser` 短路。
- 修改 `app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt`：开关行、扫码入口、覆盖层接线、消息配色。
- 创建 `app/src/main/java/com/pulse/tts/ui/components/QrScannerOverlay.kt`：相机预览 + 识别。
- 修改 `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`：依赖与相机权限。
- 修改 `app/src/test/java/com/pulse/tts/data/AppSettingsTest.kt`：默认值与序列化测试。

---

### 任务 1：设置字段与默认值

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/data/AppSettings.kt`
- 测试：`app/src/test/java/com/pulse/tts/data/AppSettingsTest.kt`（已存在，追加用例）

- [ ] **步骤 1：编写失败的测试**

在 `AppSettingsTest` 中追加：

```kotlin
    @Test
    fun `局域网自动允许默认关闭`() {
        assertFalse(AppSettings().lanSyncAutoAllow)
    }

    @Test
    fun `局域网自动允许可序列化往返`() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val encoded = json.encodeToString(AppSettings.serializer(), AppSettings(lanSyncAutoAllow = true))
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)
        assertTrue(decoded.lanSyncAutoAllow)
    }
```

需要新增的 import：`kotlinx.serialization.json.Json`。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew.bat testDebugUnitTest --tests "com.pulse.tts.data.AppSettingsTest"`
预期：编译失败，`lanSyncAutoAllow` 未定义。

- [ ] **步骤 3：实现字段**

```kotlin
@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val backgroundPlay: Boolean = true,
    val resume: Boolean = true,
    val autoTestOnLaunch: Boolean = true,
    val cooldownMinutes: Int = 10,
    val failThreshold: Int = 3,
    val sleepMinutes: Int = 0,
    val lanSyncAutoAllow: Boolean = false,
)
```

旧版本已保存的 JSON 不含该字段；`SettingsStore` 的 `Json { ignoreUnknownKeys = true }` 与默认值可正常兼容，无需迁移。

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew.bat testDebugUnitTest --tests "com.pulse.tts.data.AppSettingsTest"`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/pulse/tts/data/AppSettings.kt app/src/test/java/com/pulse/tts/data/AppSettingsTest.kt
git commit -m "feat(lan-sync): 新增自动允许设置字段"
```

---

### 任务 2：ViewModel 自动确认

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt`

- [ ] **步骤 1：注入开关**

```kotlin
class LanSyncViewModel(
    private val context: Context,
    private val repo: NodeRepository,
    private val autoAllow: () -> Boolean = { false },
) : ViewModel() {
```

默认值 `{ false }` 保证现有调用方与测试无需改动。

- [ ] **步骤 2：抽取合并逻辑并短路 `askUser`**

现有 `askUser` 里「合并 + 回传」的三行提取成私有函数，手动与自动两条路径共用：

```kotlin
    private suspend fun askUser(request: IncomingRequest): SyncDecision? {
        if (autoAllow()) {
            _state.update {
                it.copy(
                    phase = LanSyncPhase.RUNNING,
                    pending = null,
                    message = "已自动允许来自 ${request.from.name} 的同步",
                )
            }
            return acceptIncoming(request)
        }
        val deferred = CompletableDeferred<Boolean>()
        decision = deferred
        _state.update {
            it.copy(
                phase = LanSyncPhase.RUNNING,
                pending = PendingSync(UUID.randomUUID().toString(), request.from.name, request.nodes),
            )
        }
        val accepted = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() }
        decision = null
        _state.update { it.copy(pending = null) }
        if (accepted == null) return null
        if (!accepted) return SyncDecision.Reject
        return acceptIncoming(request)
    }

    private suspend fun acceptIncoming(request: IncomingRequest): SyncDecision {
        val stats = repo.mergeSynced(request.nodes)
        _state.update { it.copy(phase = LanSyncPhase.DONE, stats = stats) }
        return SyncDecision.Accept(toSyncedNodes(repo.nodes.value), stats.total)
    }
```

注意 `SyncDecision.Accept(nodes, applied)` 的第一个参数是**合并后的本机节点**，第二个是本次应用数量 `stats.total`；不要传 `request.nodes`。

`X-Sync-Session` 校验在 `SyncServer` 内先于 `confirm` 执行，自动允许不绕过鉴权。

- [ ] **步骤 3：编译验证**

运行：`./gradlew.bat assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/pulse/tts/viewmodel/LanSyncViewModel.kt
git commit -m "feat(lan-sync): ViewModel 支持自动允许"
```

---

### 任务 3：同步页开关

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt`

- [ ] **步骤 1：构造 ViewModel 时传入开关读取**

```kotlin
    val container = LocalAppContainer.current
    val appSettings by container.settings.appSettings.collectAsState()
    val vm: LanSyncViewModel = appViewModel {
        LanSyncViewModel(
            context = it.appContext,
            repo = it.nodeRepository,
            autoAllow = { it.settings.appSettings.value.lanSyncAutoAllow },
        )
    }
```

回调用 `value` 实时读取，切换开关立即生效，无需重建 VM。

- [ ] **步骤 2：加开关行**

`SettingsScreen` 里的 `SwitchSettingRow` 是 `private`，不可复用；在 `LanSyncScreen.kt` 内用公共 `SettingRow` + `Switch` 组合：

```kotlin
            Spacer(Modifier.height(8.dp))
            SettingGroup {
                SettingRow(
                    title = "自动允许同步请求",
                    subtitle = "开启后不再弹窗确认，仅建议在可信任的网络中使用。",
                    icon = Icons.Rounded.VerifiedUser,
                    onClick = {
                        container.settings.updateAppSettings {
                            it.copy(lanSyncAutoAllow = !it.lanSyncAutoAllow)
                        }
                    },
                    trailing = {
                        Switch(
                            checked = appSettings.lanSyncAutoAllow,
                            onCheckedChange = { value ->
                                container.settings.updateAppSettings {
                                    it.copy(lanSyncAutoAllow = value)
                                }
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
```

位置：介绍文案之后、「本机配对串」`SettingGroup` 之前。

新增 import：`androidx.compose.material.icons.rounded.VerifiedUser`、`androidx.compose.material3.Switch`、`com.pulse.tts.ui.LocalAppContainer`、`com.pulse.tts.ui.components.SettingRow`。

- [ ] **步骤 3：状态消息不再一律标红**

自动允许成功时 `message` 非空但 `phase == DONE`，当前实现会把它渲染成错误色。改为：

```kotlin
            state.message?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.phase == LanSyncPhase.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
```

- [ ] **步骤 4：编译验证**

运行：`./gradlew.bat assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt
git commit -m "feat(lan-sync): 同步页自动允许开关"
```

---

### 任务 4：相机依赖与权限

**文件：**
- 修改：`app/build.gradle.kts`
- 修改：`app/src/main/AndroidManifest.xml`

- [ ] **步骤 1：添加依赖**

在 `dependencies { }` 的「网络」区块前插入：

```kotlin
    // 相机 + 二维码识别（bundled 模型，不依赖 GMS）
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
```

许可证：CameraX 与 ML Kit 均为 Apache-2.0；预计 APK 增加 3~4 MB。

- [ ] **步骤 2：声明权限**

在 `AndroidManifest.xml` 的 `POST_NOTIFICATIONS` 之后插入：

```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <!-- 无相机设备仍可安装，仅扫码入口不可用 -->
    <uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **步骤 3：同步与编译**

运行：`./gradlew.bat assembleDebug`
预期：BUILD SUCCESSFUL（首次会下载依赖）。

- [ ] **步骤 4：Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat(scan): 接入 CameraX 与 ML Kit 并声明相机权限"
```

---

### 任务 5：扫码覆盖层

**文件：**
- 创建：`app/src/main/java/com/pulse/tts/ui/components/QrScannerOverlay.kt`

- [ ] **步骤 1：实现组件**

回调返回 `Boolean`：`true` 表示已接受（调用方负责关闭覆盖层），`false` 表示不是配对码，覆盖层继续扫并自行提示。

```kotlin
package com.pulse.tts.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val HINT_DEDUPE_MS = 1_500L

/**
 * 全屏扫码层：CameraX 预览 + ML Kit 识别。
 *
 * [onDecoded] 返回 true 表示已接受该文本，调用方应关闭本层；
 * 返回 false 表示内容不匹配，本层继续扫描并提示。
 */
@Composable
fun QrScannerOverlay(
    onClose: () -> Unit,
    onDecoded: (String) -> Boolean,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    var hint by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            QrCameraPreview(
                onText = { raw ->
                    if (!onDecoded(raw)) hint = "不是 Pulse 配对码"
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = hint ?: "对准电脑上的二维码",
                color = if (hint == null) Color.White else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            )
        } else {
            Text(
                text = "需要相机权限，可在系统设置中开启；也可以继续粘贴配对串。",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭扫码", tint = Color.White)
        }
    }
}

@Composable
private fun QrCameraPreview(onText: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnText by rememberUpdatedState(onText)
    val executor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            camera?.cameraControl?.enableTorch(false)
            camera = null
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build(),
                    )
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder().build().also { useCase ->
                        var lastText: String? = null
                        var lastAt = 0L
                        useCase.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { codes ->
                                    val text = codes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                    val now = System.currentTimeMillis()
                                    if (text != lastText || now - lastAt > HINT_DEDUPE_MS) {
                                        lastText = text
                                        lastAt = now
                                        currentOnText(text)
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        IconButton(
            onClick = {
                val next = !torchOn
                torchOn = next
                camera?.cameraControl?.enableTorch(next)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                tint = Color.White,
            )
        }
    }
}
```

要点：
- `ImageAnalysis` 的分析器在单线程 executor 上跑，`proxy.close()` 在 ML Kit 完成回调里调用，避免 analyzer 阻塞。
- 同一文本在 1.5 秒内不重复回调，防止提示刷屏。
- 离开组合时 `executor.shutdown()` 并关手电筒；`provider` 随 `PreviewView` 销毁自动解绑，无需额外持有。

- [ ] **步骤 2：编译验证**

运行：`./gradlew.bat assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/pulse/tts/ui/components/QrScannerOverlay.kt
git commit -m "feat(scan): 应用内二维码扫描覆盖层"
```

---

### 任务 6：同步页扫码入口与结果处理

**文件：**
- 修改：`app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt`

- [ ] **步骤 1：状态与入口**

```kotlin
    var scanning by remember { mutableStateOf(false) }
```

```kotlin
            PulseSubHeader(
                title = "局域网同步",
                subtitle = "与电脑双向合并节点",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { scanning = true }) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码")
                    }
                },
            )
```

新增 import：`androidx.compose.material.icons.outlined.QrCodeScanner`、`androidx.compose.material3.IconButton`、`com.pulse.tts.ui.components.QrScannerOverlay`。

- [ ] **步骤 2：覆盖层与识别结果**

放在 `Scaffold` 之后、`state.pending` 弹窗之前：

```kotlin
    if (scanning) {
        QrScannerOverlay(
            onClose = { scanning = false },
            onDecoded = { raw ->
                val peer = parsePairingString(raw)
                if (peer == null) {
                    false
                } else {
                    scanning = false
                    input = raw
                    vm.syncFromString(raw)
                    true
                }
            },
        )
    }
```

`parsePairingString` 在 `LanSyncViewModel` 里已 import 同包函数，但本文件需要显式 `import com.pulse.tts.data.lansync.parsePairingString`。

- [ ] **步骤 3：编译验证**

运行：`./gradlew.bat assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/pulse/tts/ui/screens/LanSyncScreen.kt
git commit -m "feat(scan): 同步页右上角扫码入口"
```

---

### 任务 7：整体验证

- [ ] **步骤 1：单元测试与构建**

运行：`./gradlew.bat testDebugUnitTest assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 2：模拟器验收**

安装：`adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`

1. 进入 设置 → 局域网同步：右上角有扫码图标，开关默认关闭。
2. 开启开关，重启应用确认仍开启；桌面无需弹窗即完成同步，手机状态区显示「已自动允许来自 … 的同步」。
3. 关闭开关：拒绝、60 秒超时（408）提示与之前一致。
4. 点扫码 → 授予相机权限 → 扫桌面二维码 → 直接发起同步。
5. 扫无关二维码 → 覆盖层提示「不是 Pulse 配对码」并继续扫描。
6. 拒绝相机权限 → 显示权限提示，粘贴配对串路径仍可用。
7. 返回退出页面后，桌面再次发起同步失败（端口已释放），返回页面后恢复。

- [ ] **步骤 3：Commit（若有验收期修正）**

```bash
git add -A
git commit -m "test(scan): 模拟器验收修正"
```

---

## 验收

- 两端「自动允许」行为对称，默认关闭且持久化。
- 扫码入口在同步页右上角，扫到配对码直达同步，失败可继续扫。
- `testDebugUnitTest`、`assembleDebug` 通过。
