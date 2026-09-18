package com.coursetrace.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.coursetrace.app.model.ImportDraft
import com.coursetrace.app.model.ImportSource
import com.coursetrace.app.model.ScheduleTimeProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.OffsetDateTime
import kotlin.math.roundToInt

class PdfImportService(
    private val context: Context,
    private val client: OpenAiCompatibleClient,
) {
    data class Progress(
        val fraction: Float?,
        val message: String,
        val detail: String? = null,
    )

    suspend fun import(
        uri: Uri,
        sourceName: String,
        profile: com.coursetrace.app.model.ApiProfile,
        apiKey: String,
        timeProfile: ScheduleTimeProfile,
        onProgress: (Progress) -> Unit = {},
    ): Result<ImportDraft> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(Progress(0.02f, "正在读取 PDF…"))
            val fingerprint = fingerprint(uri)
            val (pages, truncated) = renderPages(uri) { completed, total ->
                val fraction = 0.05f + 0.25f * completed.toFloat() / total.coerceAtLeast(1).toFloat()
                onProgress(Progress(fraction, "正在渲染 PDF", "第 $completed/$total 页"))
            }
            require(pages.isNotEmpty()) { "PDF 没有可读取页面" }
            onProgress(Progress(0.32f, "等待中转站开始返回…", "已上传 ${pages.size} 页"))
            val raw = client.recognizeTimetable(profile, apiKey, pages, timeProfile) { received, total ->
                val responseFraction = total?.takeIf { it > 0 }?.let {
                    (received.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
                }
                onProgress(Progress(
                    fraction = responseFraction?.let { 0.32f + it * 0.53f },
                    message = "正在接收中转站结果",
                    detail = buildString {
                        append("已返回 ${formatBytes(received)}")
                        if (total != null) append(" / ${formatBytes(total)}")
                    },
                ))
            }.getOrThrow()
            onProgress(Progress(0.90f, "正在解析课程与周次…", "返回内容 ${raw.length} 个字符"))
            val payload = TimetablePayloadParser.parse(raw, timeProfile)
            require(payload.slots.isNotEmpty()) { "未识别到课程，请检查 PDF 清晰度" }
            ImportDraft(
                id = "pdf-${fingerprint.take(32)}",
                createdAt = OffsetDateTime.now().toString(),
                source = ImportSource.API_RELAY,
                sourceName = sourceName,
                slots = payload.slots,
                unscheduledCourses = payload.unscheduledCourses,
                warnings = payload.warnings + if (truncated) listOf("PDF 超过 12 页，仅识别前 12 页") else emptyList(),
                termName = payload.termName,
                termStartDate = payload.termStartDate,
                termWeekCount = payload.termWeekCount,
                sourceFingerprint = fingerprint,
            ).also {
                onProgress(Progress(1f, "识别完成", "${it.slots.size} 条定时课程"))
            }
        }
    }

    private fun renderPages(uri: Uri, onPageRendered: (completed: Int, total: Int) -> Unit): Pair<List<String>, Boolean> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开 PDF")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, 12)
                val pages = (0 until pageCount).map { index ->
                    renderer.openPage(index).use { page -> renderPage(page) }.also {
                        onPageRendered(index + 1, pageCount)
                    }
                }
                return pages to (renderer.pageCount > pageCount)
            }
        }
    }

    private fun fingerprint(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("无法读取 PDF")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MiB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun renderPage(page: PdfRenderer.Page): String {
        val maxWidth = 1600
        val scale = minOf(2f, maxWidth.toFloat() / page.width.toFloat())
        val bitmap = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output)
            bitmap.recycle()
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }
}
