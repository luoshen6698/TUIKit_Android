package io.trtc.tuikit.chat.demo.xingdun.features

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** XingDun chrome around JourneyApps' stable camera/decoder implementation. */
class XingDunQRCodeScannerActivity : BaseActivity() {

    private lateinit var root: FrameLayout
    private lateinit var scanner: DecoratedBarcodeView
    private lateinit var overlay: XingDunQRCodeOverlayView
    private lateinit var permissionPanel: LinearLayout
    private lateinit var torchButton: ImageView
    private var torchEnabled = false
    private var emitted = false

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showPermissionPanel()
    }

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { decodeQRCode(uri) }
                .onSuccess(::finishWithPayload)
                .onFailure {
                    Toast.makeText(this@XingDunQRCodeScannerActivity, R.string.xingdun_qr_unrecognized, Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun appearanceLightStatusBarsOverride(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildScanner()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::scanner.isInitialized &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            !emitted
        ) {
            startCamera()
        }
    }

    override fun onPause() {
        if (::scanner.isInitialized) scanner.pause()
        super.onPause()
    }

    private fun buildScanner() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        scanner = DecoratedBarcodeView(this).apply {
            setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
            viewFinder.visibility = View.GONE
            statusView.visibility = View.GONE
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    result?.text?.takeIf(String::isNotBlank)?.let(::finishWithPayload)
                }

                override fun possibleResultPoints(resultPoints: List<ResultPoint>?) = Unit
            })
        }
        root.addView(scanner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = XingDunQRCodeOverlayView(this)
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(buildTopBar(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64.dp(), Gravity.TOP))
        permissionPanel = buildPermissionPanel()
        root.addView(permissionPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            marginStart = 34.dp()
            marginEnd = 34.dp()
        })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun buildTopBar(): View = FrameLayout(this).apply {
        setBackgroundColor(0x33000000)
        addView(TextView(context).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = getString(R.string.xingdun_cancel)
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(52.dp(), 64.dp(), Gravity.START or Gravity.CENTER_VERTICAL))
        addView(TextView(context).apply {
            setText(R.string.xingdun_scan_qr)
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64.dp(), Gravity.CENTER).apply {
            marginStart = 124.dp()
            marginEnd = 124.dp()
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(scannerAction(R.drawable.xingdun_ic_qr_gallery, R.string.xingdun_scan_from_gallery) {
                galleryPicker.launch("image/*")
            })
            torchButton = scannerAction(R.drawable.xingdun_ic_qr_flash, R.string.xingdun_qr_flashlight) {
                torchEnabled = !torchEnabled
                if (torchEnabled) scanner.setTorchOn() else scanner.setTorchOff()
                torchButton.alpha = if (torchEnabled) 1f else 0.72f
            }
            addView(torchButton)
        }, FrameLayout.LayoutParams(104.dp(), 64.dp(), Gravity.END or Gravity.CENTER_VERTICAL))
    }

    private fun scannerAction(icon: Int, description: Int, action: () -> Unit): ImageView = ImageView(this).apply {
        setImageResource(icon)
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        contentDescription = getString(description)
        alpha = 0.9f
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(52.dp(), 52.dp())
    }

    private fun buildPermissionPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(24.dp(), 26.dp(), 24.dp(), 26.dp())
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xEE181B1E.toInt())
            cornerRadius = 18.dp().toFloat()
        }
        addView(TextView(context).apply {
            setText(R.string.xingdun_qr_camera_unavailable)
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            setText(R.string.xingdun_qr_camera_permission_hint)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFBFC5C8.toInt())
            setPadding(0, 10.dp(), 0, 18.dp())
        })
        addView(TextView(context).apply {
            setText(R.string.xingdun_open_system_settings)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF23B39C.toInt())
                cornerRadius = 12.dp().toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
        visibility = View.GONE
    }

    private fun startCamera() {
        if (!::scanner.isInitialized || emitted) return
        permissionPanel.visibility = View.GONE
        scanner.visibility = View.VISIBLE
        overlay.setCameraAvailable(true)
        scanner.resume()
    }

    private fun showPermissionPanel() {
        scanner.pause()
        scanner.visibility = View.INVISIBLE
        overlay.setCameraAvailable(false)
        permissionPanel.visibility = View.VISIBLE
    }

    private fun finishWithPayload(payload: String) {
        if (emitted) return
        if (runCatching { XingDunQRCodeParser.parse(payload) }.isFailure) {
            Toast.makeText(this, R.string.xingdun_qr_unrecognized, Toast.LENGTH_SHORT).show()
            return
        }
        emitted = true
        scanner.pause()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PAYLOAD, payload))
        finish()
    }

    private suspend fun decodeQRCode(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Unable to decode QR image")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAYLOAD = "xingdun_qr_payload"
    }
}

private class XingDunQRCodeOverlayView(context: android.content.Context) : View(context) {
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF23B39C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.dp().toFloat()
    }
    private val cornerPaint = Paint(framePaint).apply {
        strokeWidth = 4.dp().toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF35D3B9.toInt()
        strokeWidth = 2.dp().toFloat()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14.dp().toFloat()
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2_000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { scanProgress = it.animatedValue as Float; invalidate() }
    }
    private var scanProgress = 0f
    private var cameraAvailable = true

    init { isClickable = false }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cameraAvailable) return
        val area = scanRect()
        canvas.drawRect(0f, 0f, width.toFloat(), area.top, dimPaint)
        canvas.drawRect(0f, area.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, area.top, area.left, area.bottom, dimPaint)
        canvas.drawRect(area.right, area.top, width.toFloat(), area.bottom, dimPaint)
        canvas.drawRoundRect(area, 12.dp().toFloat(), 12.dp().toFloat(), framePaint)
        drawCorners(canvas, area)
        val lineY = area.top + 10.dp() + scanProgress * (area.height() - 20.dp())
        canvas.drawLine(area.left + 10.dp(), lineY, area.right - 10.dp(), lineY, linePaint)
        val hint = context.getString(R.string.xingdun_qr_center_hint)
        canvas.drawText(hint, width / 2f, area.bottom + 42.dp(), textPaint)
    }

    fun setCameraAvailable(available: Boolean) {
        cameraAvailable = available
        visibility = if (available) VISIBLE else INVISIBLE
        invalidate()
    }

    private fun scanRect(): RectF {
        val size = width * 0.65f
        return RectF((width - size) / 2f, (height - size) / 2f, (width + size) / 2f, (height + size) / 2f)
    }

    private fun drawCorners(canvas: Canvas, area: RectF) {
        val length = 24.dp().toFloat()
        canvas.drawLine(area.left, area.top + length, area.left, area.top, cornerPaint)
        canvas.drawLine(area.left, area.top, area.left + length, area.top, cornerPaint)
        canvas.drawLine(area.right - length, area.top, area.right, area.top, cornerPaint)
        canvas.drawLine(area.right, area.top, area.right, area.top + length, cornerPaint)
        canvas.drawLine(area.left, area.bottom - length, area.left, area.bottom, cornerPaint)
        canvas.drawLine(area.left, area.bottom, area.left + length, area.bottom, cornerPaint)
        canvas.drawLine(area.right - length, area.bottom, area.right, area.bottom, cornerPaint)
        canvas.drawLine(area.right, area.bottom, area.right, area.bottom - length, cornerPaint)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
