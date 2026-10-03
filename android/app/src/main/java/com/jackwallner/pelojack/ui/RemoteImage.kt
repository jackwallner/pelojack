package com.jackwallner.pelojack.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.jackwallner.pelojack.workout.Video
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thumbnails, kept in memory and on disk so the class list loads instantly after the first time. */
private object Thumbnails {
    private val memory = LruCache<String, ImageBitmap>(48)

    suspend fun load(context: Context, urls: List<String>): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = urls.first()
        memory.get(key)?.let { return@withContext it }
        val file = File(context.cacheDir, "thumbnails/${key.hashCode().toUInt()}.jpg")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val bytes = urls.firstNotNullOfOrNull(::download) ?: return@withContext null
            file.writeBytes(bytes)
        }
        BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { memory.put(key, it) }
    }

    private fun download(url: String): ByteArray? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}

/** The still for a class video: YouTube's large thumbnail, falling back to the medium one. */
fun Video.thumbnailUrls(): List<String>? = when (this) {
    is Video.YouTube -> listOf("https://i.ytimg.com/vi/$id/maxresdefault.jpg", "https://i.ytimg.com/vi/$id/mqdefault.jpg")
    is Video.File -> null
}

@Composable
fun RemoteImage(urls: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, urls) { value = Thumbnails.load(context, urls) }
    val loaded = image
    if (loaded == null) {
        Box(modifier.background(Palette.Raised))
    } else {
        Image(loaded, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}
