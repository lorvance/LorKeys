// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import coil.load
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.isDarkColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.net.URLEncoder

object EmojiKitchenHelper {
    private const val TAG = "EmojiKitchen"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastSelectedEmoji: String? = null

    private val POPULAR_MIXINS = listOf(
        "❤️", "🔥", "✨", "😭", "😍", "😎", "🐱", "🐶", "☕", "💀", "🎉", "🥺", "😴", "🤩"
    )

    fun onEmojiSelected(
        emoji: String?,
        container: LinearLayout?,
        scrollView: HorizontalScrollView?,
        latinIME: LatinIME?
    ) {
        if (emoji.isNullOrBlank() || container == null || scrollView == null || latinIME == null) return
        val currentEmoji = emoji.trim()

        val pairs = mutableListOf<Pair<String, String>>()

        // 1. Previous emoji + current emoji
        val prev = lastSelectedEmoji
        if (prev != null && prev != currentEmoji) {
            pairs.add(Pair(prev, currentEmoji))
        }

        // 2. Self mashup
        pairs.add(Pair(currentEmoji, currentEmoji))

        // 3. Mixins
        for (mixin in POPULAR_MIXINS) {
            if (mixin != currentEmoji) {
                pairs.add(Pair(currentEmoji, mixin))
            }
        }

        lastSelectedEmoji = currentEmoji

        container.removeAllViews()
        val context = container.context
        val colors = latinIME.settings.current.mColors
        val isNight = KeyboardTheme.isDarkThemeActive(context)

        val itemSize = 52.dpToPx(context.resources).toInt()
        val itemMargin = 4.dpToPx(context.resources).toInt()
        val itemPadding = 4.dpToPx(context.resources).toInt()

        val pillBgColor = if (isNight) {
            val keyBg = colors.get(ColorType.KEY_BACKGROUND)
            if (isDarkColor(keyBg)) ColorUtils.blendARGB(keyBg, Color.WHITE, 0.12f) else 0xFF2C2D30.toInt()
        } else {
            val keyBg = colors.get(ColorType.KEY_BACKGROUND)
            if (!isDarkColor(keyBg)) ColorUtils.blendARGB(keyBg, Color.BLACK, 0.08f) else 0xFFE9EAEC.toInt()
        }

        var visibleCount = 0

        for (pair in pairs) {
            val left = pair.first
            val right = pair.second
            val encodedLeft = URLEncoder.encode(left, "UTF-8")
            val encodedRight = URLEncoder.encode(right, "UTF-8")
            val url = "https://emojik.vercel.app/s/${encodedLeft}_${encodedRight}?size=128"

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14.dpToPx(context.resources).toFloat()
                setColor(pillBgColor)
            }
            val rippleColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(if (isNight) Color.WHITE else Color.BLACK, 0x30)
            )
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14.dpToPx(context.resources).toFloat()
                setColor(Color.BLACK)
            }

            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                    setMargins(itemMargin, itemMargin, itemMargin, itemMargin)
                }
                setPadding(itemPadding, itemPadding, itemPadding, itemPadding)
                background = RippleDrawable(rippleColor, bg, mask)
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
            }

            imageView.load(url) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        imageView.visibility = View.VISIBLE
                        visibleCount++
                        if (visibleCount > 0) {
                            scrollView.visibility = View.VISIBLE
                        }
                    },
                    onError = { _, _ ->
                        imageView.visibility = View.GONE
                    }
                )
            }

            imageView.setOnClickListener { v ->
                AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, v, HapticEvent.KEY_PRESS)
                scope.launch(Dispatchers.IO) {
                    val file = downloadSticker(context, url, "${left.hashCode()}_${right.hashCode()}.png")
                    if (file != null) {
                        withContext(Dispatchers.Main) {
                            try {
                                val contentUri = "content://${context.packageName}.stickercontentprovider/stickers/kitchen/${file.name}".toUri()
                                val sent = latinIME.commitKlipyContent(contentUri, "Emoji Kitchen", "image/png")
                                if (!sent) {
                                    Toast.makeText(context, context.getString(R.string.sticker_paste_not_supported), Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to commit Emoji Kitchen sticker", e)
                            }
                        }
                    }
                }
            }

            container.addView(imageView)
        }
    }

    private fun downloadSticker(context: Context, urlString: String, fileName: String): File? {
        return try {
            val kitchenDir = File(context.filesDir, "stickers/kitchen")
            if (!kitchenDir.exists()) kitchenDir.mkdirs()
            val file = File(kitchenDir, fileName)
            if (file.exists() && file.length() > 0) return file

            val connection = URL(urlString).openConnection()
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.getInputStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading Emoji Kitchen sticker: $urlString", e)
            null
        }
    }

    fun clear(scrollView: HorizontalScrollView?, container: LinearLayout?) {
        scrollView?.visibility = View.GONE
        container?.removeAllViews()
        lastSelectedEmoji = null
    }
}
