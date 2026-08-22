package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunUploadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class XingDunAttachment(
    val uri: Uri,
    val displayName: String,
    val size: Long
)

internal enum class XingDunAttachmentError {
    TOO_MANY,
    INVALID_TYPE,
    TOO_LARGE,
    EMPTY,
    UNREADABLE,
}

internal class XingDunAttachmentException(val reason: XingDunAttachmentError) : Exception()

internal object XingDunAttachmentResolver {
    const val MAX_COUNT = 5
    const val MAX_BYTES = 10L * 1024L * 1024L

    suspend fun metadata(context: Context, uris: List<Uri>): List<XingDunAttachment> = withContext(Dispatchers.IO) {
        if (uris.size > MAX_COUNT) throw XingDunAttachmentException(XingDunAttachmentError.TOO_MANY)
        uris.distinct().map { uri ->
            val values = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    Pair(
                        nameIndex.takeIf { it >= 0 }?.let(cursor::getString).orEmpty(),
                        sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: -1L
                    )
                }
            }
            val size = values?.second ?: -1L
            if (size > MAX_BYTES) throw XingDunAttachmentException(XingDunAttachmentError.TOO_LARGE)
            XingDunAttachment(
                uri = uri,
                displayName = values?.first?.takeIf(String::isNotBlank) ?: "image",
                size = size
            )
        }
    }

    suspend fun uploadFiles(context: Context, attachments: List<XingDunAttachment>): List<XingDunUploadFile> =
        withContext(Dispatchers.IO) {
            if (attachments.size > MAX_COUNT) throw XingDunAttachmentException(XingDunAttachmentError.TOO_MANY)
            attachments.mapIndexed { index, attachment ->
                val bytes = runCatching {
                    context.contentResolver.openInputStream(attachment.uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) {
                                throw XingDunAttachmentException(XingDunAttachmentError.TOO_LARGE)
                            }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                }.getOrElse { error ->
                    if (error is XingDunAttachmentException) throw error
                    throw XingDunAttachmentException(XingDunAttachmentError.UNREADABLE)
                } ?: throw XingDunAttachmentException(XingDunAttachmentError.UNREADABLE)
                if (bytes.isEmpty()) throw XingDunAttachmentException(XingDunAttachmentError.EMPTY)
                val format = imageFormat(bytes)
                    ?: throw XingDunAttachmentException(XingDunAttachmentError.INVALID_TYPE)
                XingDunUploadFile(
                    fieldName = "screenshots[]",
                    fileName = "evidence-${index + 1}.${format.extension}",
                    mimeType = format.mimeType,
                    bytes = bytes
                )
            }
        }

    internal fun imageFormat(bytes: ByteArray): ImageFormat? {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return ImageFormat("image/jpeg", "jpg")
        }
        if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return ImageFormat("image/png", "png")
        }
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII).uppercase(Locale.ROOT) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII).uppercase(Locale.ROOT) == "WEBP"
        ) {
            return ImageFormat("image/webp", "webp")
        }
        return null
    }

    internal data class ImageFormat(val mimeType: String, val extension: String)
}
