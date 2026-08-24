package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import io.trtc.tuikit.chat.app.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class XingDunPersonalQRCodeArtifact(
    val payload: String,
    val image: Bitmap,
    val shareText: String,
    val expiresAtMillis: Long,
)

/** Builds and caches the same shareable personal QR card used by the iOS client. */
class XingDunPersonalQRCodeArtifactStore(private val context: Context) {

    fun artifact(
        tenantKey: String,
        userID: String,
        displayName: String,
        accountID: String,
        avatarURL: String?,
    ): XingDunPersonalQRCodeArtifact {
        require(userID.isNotBlank())
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val payload = "{\"app\":\"XingDun\",\"type\":\"user\",\"user_id\":\"${jsonEscape(userID)}\",\"version\":1}"
        val shareText = context.getString(
            R.string.xingdun_personal_qr_share_text,
            context.getString(R.string.demo_app_name),
            displayName,
            accountID,
        )
        val cacheKey = stableHash(listOf(tenantKey, userID, displayName, accountID, avatarURL.orEmpty(), locale.toLanguageTag()).joinToString("|"))
        val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val imageFile = File(cacheDirectory, "$cacheKey.png")
        val preferences = context.getSharedPreferences(CACHE_PREFERENCES, Context.MODE_PRIVATE)
        val expiresKey = "$cacheKey.expires"
        val now = System.currentTimeMillis()
        val cachedExpiry = preferences.getLong(expiresKey, 0L)
        if (now < cachedExpiry) {
            BitmapFactory.decodeFile(imageFile.absolutePath)?.let { cached ->
                return XingDunPersonalQRCodeArtifact(payload, cached, shareText, cachedExpiry)
            }
        }

        val expiresAt = now + VALIDITY_MILLIS
        val qrCode = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 720, 720)
        val avatar = avatarURL?.takeIf(String::isNotBlank)?.let(::downloadBitmap)
        val card = renderCard(qrCode, avatar, displayName, expiresAt, locale)
        runCatching {
            FileOutputStream(imageFile).use { output -> check(card.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            preferences.edit().putLong(expiresKey, expiresAt).apply()
        }
        return XingDunPersonalQRCodeArtifact(payload, card, shareText, expiresAt)
    }

    private fun renderCard(
        qrCode: Bitmap,
        avatar: Bitmap?,
        displayName: String,
        expiresAt: Long,
        locale: Locale,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(1_080, 1_500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        drawAvatar(canvas, paint, avatar, displayName)
        drawText(canvas, paint, displayName, 250f, 166f, 58f, Color.BLACK, Paint.Align.LEFT, 720f, true)
        paint.color = 0xFF909295.toInt()
        canvas.drawRect(80f, 310f, 1_000f, 312f, paint)
        paint.isAntiAlias = false
        canvas.drawBitmap(qrCode, null, RectF(180f, 345f, 900f, 1_065f), paint)
        paint.isAntiAlias = true
        drawText(
            canvas,
            paint,
            context.getString(R.string.xingdun_personal_qr_scan_hint),
            540f,
            1_145f,
            38f,
            0xFF949699.toInt(),
            Paint.Align.CENTER,
            900f,
            false,
        )
        val isChinese = locale.language == Locale.CHINESE.language
        val formatter = SimpleDateFormat(if (isChinese) "yyyy年M月d日 HH:mm" else "MMM d, yyyy HH:mm", locale)
        val validity = context.getString(R.string.xingdun_personal_qr_validity_format, formatter.format(Date(expiresAt)))
        drawText(canvas, paint, validity, 540f, 1_280f, 34f, 0xFFFA9414.toInt(), Paint.Align.CENTER, 920f, true)
        return bitmap
    }

    private fun drawAvatar(canvas: Canvas, paint: Paint, avatar: Bitmap?, displayName: String) {
        val bounds = RectF(90f, 78f, 210f, 198f)
        canvas.save()
        canvas.clipPath(Path().apply { addOval(bounds, Path.Direction.CW) })
        if (avatar != null) {
            canvas.drawBitmap(avatar, null, bounds, paint)
        } else {
            paint.color = 0xFFE0F3EF.toInt()
            canvas.drawOval(bounds, paint)
            drawText(canvas, paint, displayName.trim().take(1).uppercase(), 150f, 158f, 52f, 0xFF128B78.toInt(), Paint.Align.CENTER, 100f, true)
        }
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xFFE3E6E8.toInt()
        canvas.drawOval(bounds, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        baseline: Float,
        initialSize: Float,
        color: Int,
        align: Paint.Align,
        maxWidth: Float,
        bold: Boolean,
    ) {
        paint.color = color
        paint.textAlign = align
        paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        paint.textSize = initialSize
        while (paint.textSize > 22f && paint.measureText(text) > maxWidth) paint.textSize -= 1f
        canvas.drawText(text, x, baseline, paint)
    }

    private fun downloadBitmap(value: String): Bitmap? = runCatching {
        val connection = URL(value).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use(BitmapFactory::decodeStream)
    }.getOrNull()

    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

    private fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "xingdun-personal-qr-v4"
        const val CACHE_PREFERENCES = "xingdun_personal_qr_cache"
        const val VALIDITY_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}
