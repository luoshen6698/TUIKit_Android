package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Canvas counterpart of the iOS EnterpriseCircuitBackdrop used on authentication screens. */
class XingDunCircuitBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val circuitPath = Path()
    private val signalPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            if (dark) intArrayOf(Color.rgb(3, 18, 19), Color.rgb(7, 60, 56), Color.rgb(5, 46, 41))
            else intArrayOf(Color.rgb(234, 248, 244), Color.rgb(221, 244, 236), Color.rgb(232, 247, 241)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = RadialGradient(
            width.toFloat(),
            0f,
            width * 0.9f,
            intArrayOf(withAlpha(Color.rgb(141, 235, 216), if (dark) 51 else 66), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = RadialGradient(
            0f,
            height.toFloat(),
            width * 0.7f,
            intArrayOf(withAlpha(Color.rgb(244, 189, 72), if (dark) 20 else 28), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        buildPaths()
        paint.style = Paint.Style.STROKE
        paint.color = withAlpha(Color.rgb(24, 141, 121), if (dark) 36 else 33)
        paint.pathEffect = null
        canvas.drawPath(circuitPath, paint)

        paint.color = withAlpha(Color.rgb(244, 189, 72), 56)
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f * density, 7f * density), 0f)
        canvas.drawPath(signalPath, paint)
        paint.pathEffect = null

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(244, 189, 72)
        canvas.drawCircle(width * 0.13f, height * 0.23f, 2.5f * density, paint)
        canvas.drawCircle(width * 0.88f, height * 0.67f, 2.5f * density, paint)
    }

    private fun buildPaths() {
        val w = width.toFloat()
        val h = height.toFloat()
        circuitPath.reset()
        circuitPath.moveTo(0f, h * 0.18f)
        circuitPath.lineTo(w * 0.18f, h * 0.18f)
        circuitPath.lineTo(w * 0.30f, h * 0.27f)
        circuitPath.lineTo(w * 0.54f, h * 0.27f)
        circuitPath.moveTo(w, h * 0.48f)
        circuitPath.lineTo(w * 0.83f, h * 0.48f)
        circuitPath.lineTo(w * 0.72f, h * 0.57f)
        circuitPath.lineTo(w * 0.46f, h * 0.57f)
        circuitPath.moveTo(0f, h * 0.82f)
        circuitPath.lineTo(w * 0.24f, h * 0.82f)
        circuitPath.lineTo(w * 0.34f, h * 0.74f)

        signalPath.reset()
        signalPath.moveTo(w * 0.72f, 0f)
        signalPath.lineTo(w * 0.72f, h * 0.16f)
        signalPath.lineTo(w * 0.86f, h * 0.25f)
        signalPath.moveTo(w * 0.12f, h)
        signalPath.lineTo(w * 0.12f, h * 0.72f)
        signalPath.lineTo(w * 0.24f, h * 0.64f)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
