package io.trtc.tuikit.chat.demo.xingdun.launch

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import io.trtc.tuikit.chat.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * XingDun brand mark shared by enterprise access and authentication.
 *
 * The local artwork is always rendered first. A configured HTTPS enterprise logo may replace it,
 * while the signal arc follows the iOS 5.5-second rotation and the system animation preference.
 */
class XingDunEnterpriseLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val ringBounds = RectF()
    private val baseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(41, 190, 164)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 52)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
    }
    private val signalDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 183, 52)
        style = Paint.Style.FILL
    }
    private val logo = ImageView(context).apply {
        contentDescription = resources.getString(R.string.demo_app_name)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(3, 15, 43))
        }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setImageResource(R.drawable.xingdun_brand_logo)
    }

    private var angle = 0f
    private var logoLoadJob: Job? = null
    private var logoRequestGeneration = 0
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = SIGNAL_ROTATION_DURATION_MILLIS
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
        addView(
            logo,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = android.view.Gravity.CENTER
                val inset = (12f * density).toInt()
                setMargins(inset, inset, inset, inset)
            }
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        logoLoadJob?.cancel()
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateAnimationState()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val inset = 5.5f * density
        ringBounds.set(inset, inset, width - inset, height - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringBounds.isEmpty) return
        canvas.drawOval(ringBounds, baseRingPaint)
        val startAngle = -90f + angle
        val sweepAngle = 104f
        canvas.drawArc(ringBounds, startAngle, sweepAngle, false, signalPaint)
        val radians = Math.toRadians((startAngle + sweepAngle).toDouble())
        val radius = ringBounds.width() / 2f
        val dotX = ringBounds.centerX() + kotlin.math.cos(radians).toFloat() * radius
        val dotY = ringBounds.centerY() + kotlin.math.sin(radians).toFloat() * radius
        canvas.drawCircle(dotX, dotY, 3.5f * density, signalDotPaint)
    }

    fun loadLogo(scope: CoroutineScope, remoteUrl: String?) {
        val generation = ++logoRequestGeneration
        logoLoadJob?.cancel()
        showLocalLogo()
        if (!XingDunRemoteLogoPolicy.isAllowed(remoteUrl)) return
        logoLoadJob = scope.launch {
            val bitmap = XingDunRemoteLogoLoader.load(remoteUrl.orEmpty()) ?: return@launch
            if (generation == logoRequestGeneration) showBitmap(bitmap)
        }
    }

    private fun showLocalLogo() {
        logo.setImageResource(R.drawable.xingdun_brand_logo)
    }

    private fun showBitmap(bitmap: Bitmap) {
        logo.setImageBitmap(bitmap)
    }

    private fun updateAnimationState() {
        val shouldAnimate = isVisible && systemAnimationsEnabled()
        if (shouldAnimate && !animator.isStarted) {
            animator.start()
        } else if (!shouldAnimate && animator.isStarted) {
            animator.cancel()
            angle = 0f
            invalidate()
        }
    }

    @SuppressLint("NewApi")
    private fun systemAnimationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled()
        return runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }

    companion object {
        internal const val SIGNAL_ROTATION_DURATION_MILLIS = 5_500L
    }
}
