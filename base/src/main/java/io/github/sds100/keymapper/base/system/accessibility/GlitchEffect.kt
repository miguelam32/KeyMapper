package io.github.sds100.keymapper.base.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlin.random.Random
import timber.log.Timber

object GlitchEffect {

    fun show(service: AccessibilityService, paused: Boolean, onFail: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFail()
            return
        }
        capture(service, paused, onFail)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun capture(service: AccessibilityService, paused: Boolean, onFail: () -> Unit) {
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer = screenshot.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            buffer.close()
                            if (soft == null) {
                                onFail()
                            } else {
                                showOverlay(service, soft, paused)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Glitch failed")
                            onFail()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        onFail()
                    }
                },
            )
        } catch (e: Exception) {
            Timber.e(e, "Screenshot failed")
            onFail()
        }
    }

    private fun showOverlay(service: AccessibilityService, bitmap: Bitmap, paused: Boolean) {
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Azul = pausado (apagado), verde = activo (prendido)
        val tint = if (paused) 0xFF1E88FF.toInt() else 0xFF00E676.toInt()
        val view = GlitchView(service, bitmap, tint)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        windowManager.addView(view, params)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener {
                view.progress = it.animatedValue as Float
                view.invalidate()
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            windowManager.removeView(view)
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                        bitmap.recycle()
                    }
                },
            )
            start()
        }
    }

    private class GlitchView(
        context: Context,
        private val bitmap: Bitmap,
        tint: Int,
    ) : View(context) {
        var progress = 0f

        private val rnd = Random(System.nanoTime())
        private val bitmapPaint = Paint()
        private val dotPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        private val src = Rect()
        private val dst = Rect()
        private val tintFilter = buildFilter(tint)
        private var lastUpdate = 0L
        private var heights = IntArray(0)
        private var offsets = FloatArray(0)
        private var tinted = true

        private fun buildFilter(tint: Int): ColorMatrixColorFilter {
            val tr = Color.red(tint) / 255f
            val tg = Color.green(tint) / 255f
            val tb = Color.blue(tint) / 255f
            // gris -> contraste alto -> dos colores (negro y el tinte)
            val a = 4f
            val b = -1.8f * 255f
            val matrix = ColorMatrix(
                floatArrayOf(
                    tr * a * 0.299f, tr * a * 0.587f, tr * a * 0.114f, 0f, tr * b,
                    tg * a * 0.299f, tg * a * 0.587f, tg * a * 0.114f, 0f, tg * b,
                    tb * a * 0.299f, tb * a * 0.587f, tb * a * 0.114f, 0f, tb * b,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            return ColorMatrixColorFilter(matrix)
        }

        private fun regenerate(h: Int) {
            val hs = ArrayList<Int>()
            val os = ArrayList<Float>()
            var total = 0
            while (total < h) {
                val sliceH = rnd.nextInt(h / 40 + 1, h / 8 + 2)
                hs.add(sliceH)
                os.add(if (rnd.nextFloat() < 0.35f) rnd.nextFloat() * 2f - 1f else 0f)
                total += sliceH
            }
            heights = hs.toIntArray()
            offsets = os.toFloatArray()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            if (w == 0 || h == 0) return

            val intensity = kotlin.math.sin(Math.PI * progress).toFloat()

            val now = SystemClock.uptimeMillis()
            if (now - lastUpdate > 70 || heights.isEmpty()) {
                lastUpdate = now
                regenerate(h)
                tinted = rnd.nextFloat() < 0.85f
            }

            canvas.drawColor(Color.BLACK)
            bitmapPaint.colorFilter = if (tinted) tintFilter else null

            val scaleY = bitmap.height.toFloat() / h
            val maxShift = w * 0.15f * intensity
            var y = 0
            var i = 0
            while (y < h && i < heights.size) {
                val sliceH = heights[i]
                val shift = (offsets[i] * maxShift).toInt()
                src.set(0, (y * scaleY).toInt(), bitmap.width, ((y + sliceH) * scaleY).toInt())
                dst.set(shift, y, w + shift, y + sliceH)
                canvas.drawBitmap(bitmap, src, dst, bitmapPaint)
                y += sliceH
                i++
            }

            // puntos tipo halftone
            val radius = 7f * intensity
            if (radius > 0.5f) {
                var row = 0
                var gy = 0
                while (gy < h) {
                    var gx = if (row % 2 == 0) 0 else 11
                    while (gx < w) {
                        canvas.drawCircle(gx.toFloat(), gy.toFloat(), radius, dotPaint)
                        gx += 22
                    }
                    gy += 22
                    row++
                }
            }
        }
    }
}
