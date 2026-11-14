package com.pulse.tts.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装与权限跳转：只处理系统安装器这一层。
 *
 * 未授予「安装未知应用」时先跳到本应用的授权页，由用户手动打开开关后重试。
 */
object ApkInstaller {

    enum class Result {
        STARTED,
        NEED_PERMISSION,
        FAILED,
    }

    fun install(context: Context, file: File): Result {
        if (!file.exists()) return Result.FAILED
        if (!canInstall(context)) {
            openUnknownSourcesSettings(context)
            return Result.NEED_PERMISSION
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.STARTED
        }.getOrDefault(Result.FAILED)
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 浏览器兜底：打开 Release 页，交给用户自行选择 ABI。 */
    fun openReleasePage(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
