package com.coursetrace.app.ui.theme

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@Composable
fun CourseTraceBackground(
    imageUri: String,
    overlayAlpha: Float,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.roundToPx() }.coerceIn(1, 1440)
        val targetHeight = with(density) { maxHeight.roundToPx() }.coerceIn(1, 2560)
        val bitmap by produceState<ImageBitmap?>(null, imageUri, targetWidth, targetHeight) {
            value = if (imageUri.isBlank()) null else withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(imageUri))
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val sourceWidth = info.size.width.coerceAtLeast(1)
                        val sourceHeight = info.size.height.coerceAtLeast(1)
                        val coverScale = max(
                            targetWidth.toFloat() / sourceWidth,
                            targetHeight.toFloat() / sourceHeight,
                        )
                        val decodeScale = min(coverScale, 1f)
                        decoder.setTargetSize(
                            (sourceWidth * decodeScale).toInt().coerceAtLeast(1),
                            (sourceHeight * decodeScale).toInt().coerceAtLeast(1),
                        )
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    }.asImageBitmap()
                }.getOrNull()
            }
        }

        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = overlayAlpha.coerceIn(0.35f, 0.95f))),
            )
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}
