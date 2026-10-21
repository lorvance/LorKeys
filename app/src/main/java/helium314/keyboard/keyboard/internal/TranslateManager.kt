// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.app.AlertDialog
import android.view.KeyEvent
import helium314.keyboard.latin.utils.Log
import androidx.core.graphics.ColorUtils
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext
import helium314.keyboard.latin.utils.updateSoftInputWindowLayoutParameters
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object TranslateManager {

    var isTranslateActive: Boolean = false
        private set

    private var currentSourceCode = "ru"
    private var currentTargetCode = "en"
    private var inputBuffer = StringBuilder()
    private var lastCommittedLength = 0

    private var translateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI elements in translate_bar
    private var translateBarRoot: View? = null
    private var stripContainer: View? = null
    private var btnClose: ImageButton? = null
    private var btnSrcLang: TextView? = null
    private var btnSwap: ImageButton? = null
    private var btnDstLang: TextView? = null
    private var btnClear: ImageButton? = null
    private var hsvInput: HorizontalScrollView? = null
    private var tvInput: TextView? = null
    private var pbLoading: ProgressBar? = null

    data class Lang(val code: String, val name: String)

    val ALL_LANGUAGES = listOf(
        Lang("auto", "Авто"),
        Lang("ru", "Русский"),
        Lang("en", "Английский"),
        Lang("es", "Испанский"),
        Lang("de", "Немецкий"),
        Lang("fr", "Французский"),
        Lang("it", "Итальянский"),
        Lang("tr", "Турецкий"),
        Lang("zh-CN", "Китайский"),
        Lang("ja", "Японский"),
        Lang("ko", "Корейский"),
        Lang("ar", "Арабский"),
        Lang("pt", "Португальский"),
        Lang("pl", "Польский"),
        Lang("uk", "Украинский"),
        Lang("kk", "Казахский"),
        Lang("uz", "Узбекский"),
        Lang("tg", "Таджикский"),
        Lang("be", "Белорусский"),
        Lang("hy", "Армянский"),
        Lang("ka", "Грузинский"),
        Lang("az", "Азербайджанский")
    )

    fun getTranslateBarHeight(): Int {
        val tb = translateBarRoot
        if (!isTranslateActive || tb == null || tb.visibility != View.VISIBLE) return 0
        val h = tb.height
        if (h > 0) return h
        val density = tb.resources.displayMetrics.density
        return (78 * density).toInt()
    }

    fun toggle(latinIME: LatinIME) {
        if (isTranslateActive) {
            close(latinIME)
        } else {
            open(latinIME)
        }
    }

    fun open(latinIME: LatinIME) {
        val root = KeyboardSwitcher.getInstance().currentInputView ?: latinIME.inputView ?: latinIME.window?.window?.decorView ?: return
        translateBarRoot = root.findViewById(R.id.translate_bar)
        stripContainer = root.findViewById(R.id.strip_container)

        val tb = translateBarRoot ?: return
        bindViews(tb, latinIME)
        updateLanguageLabels()
        applyThemeColors(tb, latinIME)

        stripContainer?.visibility = View.GONE
        tb.visibility = View.VISIBLE
        isTranslateActive = true
        lastCommittedLength = 0
        inputBuffer.clear()

        updateInputDisplay()

        latinIME.inputView?.requestLayout()
        latinIME.window?.window?.decorView?.requestLayout()
        latinIME.updateSoftInputWindowLayoutParameters(latinIME.inputView, true)
    }

    fun close(latinIME: LatinIME?) {
        isTranslateActive = false
        translateJob?.cancel()
        inputBuffer.clear()
        lastCommittedLength = 0

        translateBarRoot?.visibility = View.GONE
        stripContainer?.visibility = View.VISIBLE

        latinIME?.inputView?.requestLayout()
        latinIME?.window?.window?.decorView?.requestLayout()
        if (latinIME != null) {
            latinIME.updateSoftInputWindowLayoutParameters(latinIME.inputView, true)
        }
    }

    private fun bindViews(tb: View, latinIME: LatinIME) {
        btnClose = tb.findViewById(R.id.btn_tb_close)
        btnSrcLang = tb.findViewById(R.id.btn_tb_src_lang)
        btnSwap = tb.findViewById(R.id.btn_tb_swap)
        btnDstLang = tb.findViewById(R.id.btn_tb_target_lang)
        btnClear = tb.findViewById(R.id.btn_tb_clear)
        hsvInput = tb.findViewById(R.id.hsv_tb_input)
        tvInput = tb.findViewById(R.id.tv_tb_input)
        pbLoading = tb.findViewById(R.id.pb_tb_loading)

        btnClose?.setOnClickListener {
            close(latinIME)
        }

        btnSrcLang?.setOnClickListener {
            showLanguagePicker(it, isSource = true, latinIME)
        }

        btnDstLang?.setOnClickListener {
            showLanguagePicker(it, isSource = false, latinIME)
        }

        btnSwap?.setOnClickListener {
            if (currentSourceCode == "auto") {
                currentSourceCode = currentTargetCode
                currentTargetCode = if (currentSourceCode == "ru") "en" else "ru"
            } else {
                val temp = currentSourceCode
                currentSourceCode = currentTargetCode
                currentTargetCode = temp
            }
            updateLanguageLabels()
            triggerTranslation(latinIME, immediate = true)
        }

        btnClear?.setOnClickListener {
            if (lastCommittedLength > 0) {
                val ic = latinIME.currentInputConnection
                ic?.beginBatchEdit()
                ic?.deleteSurroundingText(lastCommittedLength, 0)
                ic?.endBatchEdit()
                lastCommittedLength = 0
            }
            inputBuffer.clear()
            updateInputDisplay()
        }
    }

    private fun showLanguagePicker(anchor: View, isSource: Boolean, latinIME: LatinIME) {
        val list = if (isSource) ALL_LANGUAGES else ALL_LANGUAGES.filter { it.code != "auto" }
        val names = list.map { it.name }.toTypedArray()
        val currentCode = if (isSource) currentSourceCode else currentTargetCode
        val selectedIndex = list.indexOfFirst { it.code == currentCode }.coerceAtLeast(0)

        try {
            val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(latinIME))
                .setTitle(if (isSource) "Исходный язык" else "Язык перевода")
                .setSingleChoiceItems(names, selectedIndex) { di, which ->
                    di.dismiss()
                    val selected = list.getOrNull(which) ?: return@setSingleChoiceItems
                    if (isSource) {
                        currentSourceCode = selected.code
                    } else {
                        currentTargetCode = selected.code
                    }
                    updateLanguageLabels()
                    triggerTranslation(latinIME, immediate = true)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()

            val window = dialog.window
            val layoutParams = window?.attributes
            val token = KeyboardSwitcher.getInstance().mainKeyboardView?.windowToken
                ?: latinIME.window?.window?.decorView?.windowToken
                ?: anchor.windowToken
            if (token != null) {
                layoutParams?.token = token
            }
            layoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            window?.attributes = layoutParams
            window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            dialog.show()
        } catch (e: Exception) {
            Log.e("TranslateManager", "Failed to show language picker dialog", e)
        }
    }

    private fun updateLanguageLabels() {
        val srcName = ALL_LANGUAGES.firstOrNull { it.code == currentSourceCode }?.name ?: currentSourceCode
        val dstName = ALL_LANGUAGES.firstOrNull { it.code == currentTargetCode }?.name ?: currentTargetCode
        btnSrcLang?.text = "$srcName ▾"
        btnDstLang?.text = "$dstName ▾"
    }

    private fun applyThemeColors(tb: View, context: Context) {
        val colors: Colors = runCatching { Settings.getValues().mColors }.getOrNull() ?: return
        val textColor = colors.get(ColorType.KEY_TEXT)
        val hintColor = ColorUtils.setAlphaComponent(textColor, 120)
        val cardBg = colors.get(ColorType.KEY_BACKGROUND)
        val density = context.resources.displayMetrics.density

        btnClose?.setColorFilter(textColor)
        btnSwap?.setColorFilter(textColor)
        btnClear?.setColorFilter(textColor)

        // Rounded pill styling for language buttons
        val pillBgColor = if (cardBg != Color.TRANSPARENT) cardBg else ColorUtils.setAlphaComponent(textColor, 25)
        val pillStrokeColor = ColorUtils.setAlphaComponent(textColor, 40)
        val pillRadius = 16f * density

        val srcPill = GradientDrawable().apply {
            setColor(pillBgColor)
            setStroke((1 * density).toInt(), pillStrokeColor)
            cornerRadius = pillRadius
        }
        btnSrcLang?.background = srcPill
        btnSrcLang?.setTextColor(textColor)

        val dstPill = GradientDrawable().apply {
            setColor(pillBgColor)
            setStroke((1 * density).toInt(), pillStrokeColor)
            cornerRadius = pillRadius
        }
        btnDstLang?.background = dstPill
        btnDstLang?.setTextColor(textColor)

        // Rounded capsule styling for input row
        val inputRow = tb.findViewById<LinearLayout>(R.id.translate_bar_input_row)
        val inputBorderColor = ColorUtils.setAlphaComponent(textColor, 70)
        val inputCapsule = GradientDrawable().apply {
            setColor(pillBgColor)
            setStroke((1.5f * density).toInt(), inputBorderColor)
            cornerRadius = 19f * density
        }
        inputRow?.background = inputCapsule

        tvInput?.setTextColor(textColor)
        tvInput?.setHintTextColor(hintColor)
    }

    /**
     * Handles keyboard events when Translate mode is active.
     * Returns true if event was consumed (typing into translate bar), false if event should pass through.
     */
    fun handleKeyEvent(latinIME: LatinIME, event: Event): Boolean {
        if (!isTranslateActive) return false

        val code = event.keyCode
        val codePoint = event.codePoint

        // Allow shift, symbols, subtype switch to pass through to LatinIME so keyboard layout changes
        if (code == KeyCode.SHIFT || code == KeyCode.SYMBOL || code == KeyCode.ALPHA ||
            code == KeyCode.CAPS_LOCK || code == KeyCode.NUMPAD || code == KeyCode.LANGUAGE_SWITCH) {
            return false
        }

        if (code == KeyCode.DELETE) {
            if (inputBuffer.isNotEmpty()) {
                inputBuffer.deleteCharAt(inputBuffer.length - 1)
                updateInputDisplay()
                triggerTranslation(latinIME)
            } else {
                if (lastCommittedLength > 0) {
                    val ic = latinIME.currentInputConnection
                    ic?.beginBatchEdit()
                    ic?.deleteSurroundingText(lastCommittedLength, 0)
                    ic?.endBatchEdit()
                    lastCommittedLength = 0
                }
            }
            return true
        }

        // Enter key commits and submits
        if (codePoint == '\n'.code || codePoint == 10 || code == KeyEvent.KEYCODE_ENTER) {
            lastCommittedLength = 0
            inputBuffer.clear()
            updateInputDisplay()
            val ic = latinIME.currentInputConnection
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            return true
        }

        // Space
        if (codePoint == ' '.code || codePoint == 32) {
            inputBuffer.append(" ")
            updateInputDisplay()
            triggerTranslation(latinIME)
            return true
        }

        val text = event.text
        if (!text.isNullOrEmpty()) {
            inputBuffer.append(text)
            updateInputDisplay()
            triggerTranslation(latinIME)
            return true
        }

        if (codePoint > 0) {
            val chars = Character.toChars(codePoint)
            inputBuffer.append(chars)
            updateInputDisplay()
            triggerTranslation(latinIME)
            return true
        }

        return false
    }

    private fun updateInputDisplay() {
        val text = inputBuffer.toString()
        if (text.isEmpty()) {
            tvInput?.text = ""
            btnClear?.visibility = View.GONE
        } else {
            tvInput?.text = text
            btnClear?.visibility = View.VISIBLE
        }
        hsvInput?.post {
            hsvInput?.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun triggerTranslation(latinIME: LatinIME, immediate: Boolean = false) {
        val query = inputBuffer.toString()
        if (query.trim().isEmpty()) {
            if (lastCommittedLength > 0) {
                val ic = latinIME.currentInputConnection
                ic?.beginBatchEdit()
                ic?.deleteSurroundingText(lastCommittedLength, 0)
                ic?.endBatchEdit()
                lastCommittedLength = 0
            }
            return
        }

        translateJob?.cancel()
        translateJob = scope.launch {
            if (!immediate) {
                delay(220)
            }
            pbLoading?.visibility = View.VISIBLE
            val result = withContext(Dispatchers.IO) {
                performGoogleTranslate(query, currentSourceCode, currentTargetCode)
            }
            pbLoading?.visibility = View.GONE
            if (result != null && result.isNotEmpty() && isTranslateActive) {
                applyTranslationToHost(latinIME, result)
            }
        }
    }

    private fun applyTranslationToHost(latinIME: LatinIME, translatedText: String) {
        val ic = latinIME.currentInputConnection ?: return
        try {
            ic.beginBatchEdit()
            if (lastCommittedLength > 0) {
                ic.deleteSurroundingText(lastCommittedLength, 0)
            }
            ic.commitText(translatedText, 1)
            lastCommittedLength = translatedText.length
            ic.endBatchEdit()
        } catch (e: Exception) {
            // Ignore connection error
        }
    }

    private fun performGoogleTranslate(text: String, src: String, dst: String): String? {
        return try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$src&tl=$dst&dt=t&q=$encodedText"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val sentences = jsonArray.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val s = sentences.getJSONArray(i)
                    sb.append(s.getString(0))
                }
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
