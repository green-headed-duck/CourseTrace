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
import java.time.OffsetDateTime
import kotlin.math.roundToInt

class PdfImportService(
    private val context: Context,
    private val client: OpenAiCompatibleClient,
) {
    suspend fun import(
        uri: Uri,
        sourceName: String,
        profile: com.coursetrace.app.model.ApiProfile,
        apiKey: String,
        timeProfile: ScheduleTimeProfile,
    ): Result<ImportDraft> = withContext(Dispatchers.IO) {
        runCatching {
            val (pages, truncated) = renderPages(uri)
            require(pages.isNotEmpty()) { "PDF 没有可读取页面" }
            val raw = client.recognizeTimetable(profile, apiKey, pages, timeProfile).getOrThrow()
            val payload = TimetablePayloadParser.parse(raw, timeProfile)
            require(payload.slots.isNotEmpty()) { "未识别到课程，请检查 PDF 清晰度" }
            ImportDraft(
                createdAt = OffsetDateTime.now().toString(),
                source = ImportSource.API_RELAY,
                sourceName = sourceName,
                slots = payload.slots,
                unscheduledCourses = payload.unscheduledCourses,
                warnings = payload.warnings + if (truncated) listOf("PDF 超过 12 页，仅识别前 12 页") else emptyList(),
                termName = payload.termName,
                termStartDate = payload.termStartDate,
                termWeekCount = payload.termWeekCount,
            )
        }
    }

    private fun renderPages(uri: Uri): Pair<List<String>, Boolean> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("无法打开 PDF")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = minOf(renderer.pageCount, 12)
                val pages = (0 until pageCount).map { index ->
                    renderer.openPage(index).use { page -> renderPage(page) }
                }
                return pages to (renderer.pageCount > pageCount)
            }
        }
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
