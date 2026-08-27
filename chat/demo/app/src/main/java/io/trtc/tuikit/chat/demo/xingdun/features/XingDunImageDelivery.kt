package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.trtc.tuikit.chat.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Shared image save/share primitives for XingDun QR-code pages. */
internal object XingDunImageDelivery {

    suspend fun saveToPictures(context: Context, bitmap: Bitmap, fileName: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XingDun")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create image")
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } ?: error("Unable to write image")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }.getOrElse { error ->
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val target = File(directory, "XingDun/$fileName")
            val parent = requireNotNull(target.parentFile)
            check(parent.exists() || parent.mkdirs())
            FileOutputStream(target).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            @Suppress("DEPRECATION")
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)))
        }
    }

    fun shareUri(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val directory = File(context.cacheDir, "xingdun-share").apply { mkdirs() }
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.xingdun.files", file)
    }
}
