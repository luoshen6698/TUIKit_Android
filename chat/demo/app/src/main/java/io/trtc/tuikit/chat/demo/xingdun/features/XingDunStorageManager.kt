package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal enum class XingDunCacheCategory {
    IMAGE,
    AUDIO,
    VIDEO,
    FILE,
}

internal data class XingDunCacheUsage(val bytes: Map<XingDunCacheCategory, Long>) {
    val totalBytes: Long get() = bytes.values.sum()
}

internal object XingDunStorageManager {
    suspend fun usage(context: Context): XingDunCacheUsage = withContext(Dispatchers.IO) {
        val totals = XingDunCacheCategory.entries.associateWith { 0L }.toMutableMap()
        mediaFiles(context).forEach { file ->
            category(file)?.let { category -> totals[category] = totals.getValue(category) + file.length() }
        }
        XingDunCacheUsage(totals)
    }

    suspend fun clear(context: Context, selected: Set<XingDunCacheCategory>): Long = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext 0L
        var removed = 0L
        mediaFiles(context).forEach { file ->
            if (category(file) in selected) {
                val size = file.length()
                if (file.delete()) removed += size
            }
        }
        context.cacheDir.walkBottomUp().filter(File::isDirectory).forEach { directory ->
            if (directory != context.cacheDir && directory.list()?.isEmpty() == true) directory.delete()
        }
        removed
    }

    internal fun category(fileName: String): XingDunCacheCategory? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            in IMAGE_EXTENSIONS -> XingDunCacheCategory.IMAGE
            in AUDIO_EXTENSIONS -> XingDunCacheCategory.AUDIO
            in VIDEO_EXTENSIONS -> XingDunCacheCategory.VIDEO
            in FILE_EXTENSIONS -> XingDunCacheCategory.FILE
            else -> null
        }
    }

    private fun category(file: File): XingDunCacheCategory? = category(file.name)

    private fun mediaFiles(context: Context): Sequence<File> {
        val roots = listOfNotNull(context.cacheDir, context.externalCacheDir).distinctBy { it.absolutePath }
        return roots.asSequence().flatMap { root ->
            val canonicalRoot = runCatching { root.canonicalPath + File.separator }.getOrNull()
                ?: return@flatMap emptySequence()
            root.walkTopDown().filter { candidate ->
                candidate.isFile && runCatching { candidate.canonicalPath.startsWith(canonicalRoot) }.getOrDefault(false)
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val AUDIO_EXTENSIONS = setOf("aac", "amr", "m4a", "mp3", "ogg", "opus", "wav")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "3gp")
    private val FILE_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar", "7z")
}
