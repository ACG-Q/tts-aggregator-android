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
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            scanner.close()
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
                                    val text = codes.firstOrNull()?.rawValue
                                        ?: return@addOnSuccessListener
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
