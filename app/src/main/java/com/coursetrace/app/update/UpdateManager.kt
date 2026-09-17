package com.coursetrace.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.core.content.FileProvider
import com.coursetrace.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

@Serializable
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val signature: String,
    val channel: String = "stable",
    val notes: String = "",
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult
}

class UpdateManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(manifestUrl: String, channel: String): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(manifestUrl.startsWith("https://")) { "更新清单必须使用 HTTPS" }
            val body = get(manifestUrl, maxBytes = 256 * 1024)
            val manifest = json.decodeFromString<UpdateManifest>(body.toString(Charsets.UTF_8))
            require(manifest.channel == channel) { "更新通道不匹配" }
            require(manifest.apkUrl.startsWith("https://")) { "APK 下载地址必须使用 HTTPS" }
            require(verifyManifest(manifest)) { "更新清单签名无效" }
            if (manifest.versionCode > BuildConfig.VERSION_CODE.toLong()) UpdateCheckResult.Available(manifest)
            else UpdateCheckResult.UpToDate
        }
    }

    suspend fun downloadAndInstall(manifest: UpdateManifest): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(verifyManifest(manifest)) { "更新清单签名无效" }
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(directory, "coursetrace-${manifest.versionCode}.apk")
            download(manifest.apkUrl, apk, 300L * 1024 * 1024)
            val digest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(hash.equals(manifest.sha256, ignoreCase = true)) { "APK SHA-256 校验失败" }
            verifyApk(apk, manifest)
            withContext(Dispatchers.Main) { launchInstaller(apk) }
            apk
        }
    }

    private fun verifyManifest(manifest: UpdateManifest): Boolean = runCatching {
        val canonical = listOf(
            manifest.versionCode.toString(),
            manifest.versionName,
            manifest.apkUrl,
            manifest.sha256.lowercase(),
            manifest.channel,
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(UPDATE_PUBLIC_KEY_BASE64, Base64.DEFAULT)),
        )
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(canonical)
            verify(Base64.decode(manifest.signature, Base64.DEFAULT))
        }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun verifyApk(apk: File, manifest: UpdateManifest) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("无法读取 APK 信息")
        require(archive.packageName == context.packageName) { "APK 包名不匹配" }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archiveVersion == manifest.versionCode) { "APK 版本与清单不匹配" }
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        fun signerDigests(info: android.content.pm.SigningInfo?) = info?.apkContentsSigners.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
        val archiveSigners = signerDigests(archive.signingInfo)
        val currentSigners = signerDigests(current.signingInfo)
        require(archiveSigners.isNotEmpty() && archiveSigners == currentSigners) { "APK 签名与当前应用不一致" }
    }

    private fun launchInstaller(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            error("请允许课迹安装更新后重新检查")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun get(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode in 200..299) { "更新服务器返回 HTTP ${connection.responseCode}" }
            val length = connection.contentLengthLong
            require(length < 0 || length <= maxBytes) { "更新清单过大" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= maxBytes) { "更新清单过大" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File, maxBytes: Long) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = false
        try {
            require(connection.responseCode in 200..299) { "APK 服务器返回 HTTP ${connection.responseCode}" }
            val length = connection.contentLengthLong
            require(length < 0 || length <= maxBytes) { "APK 文件异常过大" }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        require(total <= maxBytes) { "APK 文件异常过大" }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val UPDATE_PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBB4JOEe0kjBaisMW2FlKpq6xymwllaaTm8w8z3XF9Smkr9edfJoSwzwusvYTA5usf7Fv0kHboHHacoyVSe0/Ig=="
    }
}
