// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

class RoundedKeyboardFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private val outlineClipPath = Path()
    private val clipRect = RectF()
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastTopRadiusPx = -1f
    private var lastBottomRadiusPx = -1f

    private var cachedTopRadiusPx = -1f
    private var cachedBottomRadiusPx = -1f
    private val staticDustOverlay = StaticSparkleDustOverlay()
    private fun isRoundKeyboard(): Boolean {
        return context.prefs().getBoolean(Settings.PREF_ROUND_KEYBOARD, Defaults.PREF_ROUND_KEYBOARD)
    }

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.PREF_KEYBOARD_CORNER_RADIUS || key == Settings.PREF_KEYBOARD_BOTTOM_CORNER_RADIUS || key == Settings.PREF_ROUND_KEYBOARD) {
            cachedTopRadiusPx = -1f
            cachedBottomRadiusPx = -1f
            lastWidth = -1
            lastHeight = -1
            invalidateOutline()
            postInvalidate()
        }
    }

    init {
        // Also clip via HWUI so hardware-layered descendants respect the rounded keyboard shape.
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                try {
                    if (view.width <= 0 || view.height <= 0) {
                        outline.setEmpty()
                        return
                    }
                    if (isRoundKeyboard()) {
                        val insetX = (8f * resources.displayMetrics.density).toInt()
                        outline.setOval(insetX, 0, view.width - insetX, view.height)
                        return
                    }
                    val topRadiusPx = if (cachedTopRadiusPx >= 0f) cachedTopRadiusPx else keyboardTopCornerRadiusPx()
                    val bottomRadiusPx = if (cachedBottomRadiusPx >= 0f) cachedBottomRadiusPx else keyboardBottomCornerRadiusPx()
                    val maxRadius = maxOf(topRadiusPx, bottomRadiusPx)
                    if (maxRadius <= 0f) {
                        outline.setEmpty()
                        return
                    }
                    outline.setRoundRect(0, 0, view.width, view.height, maxRadius)
                } catch (_: Throwable) {
                    outline.setEmpty()
                }
            }
        }
        clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.prefs().registerOnSharedPreferenceChangeListener(prefListener)
        cachedTopRadiusPx = -1f
        cachedBottomRadiusPx = -1f
        invalidateOutline()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.prefs().unregisterOnSharedPreferenceChangeListener(prefListener)
        staticDustOverlay.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Rebuild outline as soon as the IME view gets its real size.
        lastWidth = -1
        lastHeight = -1
        invalidateOutline()
        postInvalidateOnAnimation()
    }

    override fun draw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.draw(canvas)
            return
        }
        val isRound = isRoundKeyboard()
        if (cachedTopRadiusPx < 0f) {
            cachedTopRadiusPx = keyboardTopCornerRadiusPx()
        }
        if (cachedBottomRadiusPx < 0f) {
            cachedBottomRadiusPx = keyboardBottomCornerRadiusPx()
        }
        val topRadiusPx = cachedTopRadiusPx
        val bottomRadiusPx = cachedBottomRadiusPx
        if (!isRound && topRadiusPx <= 0f && bottomRadiusPx <= 0f) {
            super.draw(canvas)
            return
        }

        updateClipPath(topRadiusPx, bottomRadiusPx, isRound)
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (shouldDrawStaticDust()) {
            staticDustOverlay.draw(
                canvas,
                width,
                height,
                staticDustAlpha(),
                staticSparkleColors(),
                staticSparkleUseScreenBlend()
            )
        } else {
            staticDustOverlay.clear()
        }
        super.dispatchDraw(canvas)
    }

    private fun keyboardTopCornerRadiusPx(): Float {
        val radiusDp = Settings.readKeyboardCornerRadius(context.prefs()).coerceIn(
                Settings.KEYBOARD_CORNER_RADIUS_MIN_DP,
                Settings.KEYBOARD_CORNER_RADIUS_MAX_DP
            )
        return radiusDp * resources.displayMetrics.density
    }

    private fun keyboardBottomCornerRadiusPx(): Float {
        val radiusDp = Settings.readKeyboardBottomCornerRadius(context.prefs()).coerceIn(
                Settings.KEYBOARD_BOTTOM_CORNER_RADIUS_MIN_DP,
                Settings.KEYBOARD_BOTTOM_CORNER_RADIUS_MAX_DP
            )
        val density = resources.displayMetrics.density
        return if (radiusDp > 0) (radiusDp * 1.35f + 4f) * density else 0f
    }

    private fun shouldDrawStaticDust(): Boolean {
        if (Defaults.LIMIT_EXPENSIVE_RENDERING) return false
        val isFrosted = runCatching { Settings.getValues()?.mColors?.isFrosted == true }.getOrDefault(false)
        return isFrosted && staticDustEnabled()
    }

    private fun staticDustEnabled(): Boolean {
        return KeyboardTheme.livePreviewValues?.dustEnabled
            ?: context.prefs().getBoolean(Settings.PREF_FROSTED_DUST_ENABLED, Defaults.PREF_FROSTED_DUST_ENABLED)
    }

    private fun staticDustAlpha(): Float {
        KeyboardTheme.livePreviewValues?.dustAlpha?.let {
            return renderSparkleAlpha(it)
        }

        val prefs = context.prefs()
        val alpha = if (KeyboardTheme.isDarkThemeActive(context)) {
            prefs.getFloat(
                Settings.PREF_FROSTED_DUST_ALPHA_NIGHT,
                prefs.getFloat(Settings.PREF_FROSTED_DUST_ALPHA, Defaults.PREF_FROSTED_DUST_ALPHA_NIGHT)
            )
        } else {
            prefs.getFloat(Settings.PREF_FROSTED_DUST_ALPHA, Defaults.PREF_FROSTED_DUST_ALPHA)
        }
        return renderSparkleAlpha(alpha)
    }

    private fun renderSparkleAlpha(alpha: Float): Float {
        val isDark = KeyboardTheme.isDarkThemeActive(context)
        val sliderAlpha = alpha.coerceIn(MIN_SPARKLE_ALPHA, MAX_SPARKLE_ALPHA)
        if (isDark) {
            return sliderAlpha
        }

        val progress = (sliderAlpha - MIN_SPARKLE_ALPHA) / (MAX_SPARKLE_ALPHA - MIN_SPARKLE_ALPHA)
        return MIN_SPARKLE_ALPHA + progress * (MAX_LIGHT_THEME_SPARKLE_ALPHA - MIN_SPARKLE_ALPHA)
            .coerceIn(MIN_SPARKLE_ALPHA, MAX_LIGHT_THEME_SPARKLE_ALPHA)
    }

    private fun staticSparkleColors(): IntArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return intArrayOf(
                Color.rgb(255, 244, 210),
                Color.rgb(165, 232, 255),
                Color.rgb(220, 180, 255),
                Color.rgb(255, 176, 122),
                Color.rgb(160, 195, 255)
            )
        }

        val isDark = KeyboardTheme.isDarkThemeActive(context)
        return intArrayOf(
            ContextCompat.getColor(context, android.R.color.system_accent1_100),
            ContextCompat.getColor(context, android.R.color.system_accent2_100),
            ContextCompat.getColor(context, android.R.color.system_accent3_100),
            ContextCompat.getColor(context, android.R.color.system_accent1_200),
            ContextCompat.getColor(context, android.R.color.system_accent2_200)
        ).map { polishSparkleColor(it, isDark) }.toIntArray()
    }

    private fun staticSparkleUseScreenBlend(): Boolean {
        return true
    }

    private fun polishSparkleColor(color: Int, isDark: Boolean): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = (hsl[1] * if (isDark) 1.4f else 1.15f).coerceIn(if (isDark) 0.72f else 0.48f, 1f)
        hsl[2] = if (isDark) hsl[2].coerceIn(0.76f, 0.95f) else hsl[2].coerceIn(0.84f, 0.97f)
        val polished = ColorUtils.HSLToColor(hsl)
        return if (isDark) polished else ColorUtils.blendARGB(polished, Color.WHITE, 0.28f)
    }

    private var lastIsRound = false

    private fun updateClipPath(topRadiusPx: Float, bottomRadiusPx: Float, isRound: Boolean) {
        if (lastWidth == width && lastHeight == height && lastTopRadiusPx == topRadiusPx && lastBottomRadiusPx == bottomRadiusPx && lastIsRound == isRound) {
            return
        }

        lastWidth = width
        lastHeight = height
        lastTopRadiusPx = topRadiusPx
        lastBottomRadiusPx = bottomRadiusPx
        lastIsRound = isRound
        clipPath.reset()
        if (isRound) {
            val insetX = 8f * resources.displayMetrics.density
            clipRect.set(insetX, 0f, width.toFloat() - insetX, height.toFloat())
            clipPath.addOval(clipRect, Path.Direction.CW)
        } else {
            clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
            val radii = floatArrayOf(
                topRadiusPx, topRadiusPx,
                topRadiusPx, topRadiusPx,
                bottomRadiusPx, bottomRadiusPx,
                bottomRadiusPx, bottomRadiusPx
            )
            clipPath.addRoundRect(
                clipRect,
                radii,
                Path.Direction.CW
            )
        }
    }

    private companion object {
        private const val MIN_SPARKLE_ALPHA = 1f
        private const val MAX_SPARKLE_ALPHA = 10f
        private const val MAX_LIGHT_THEME_SPARKLE_ALPHA = 20f
    }
}
