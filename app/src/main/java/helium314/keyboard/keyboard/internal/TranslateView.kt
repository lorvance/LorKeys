// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@SuppressLint("ViewConstructor")
class TranslateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    lateinit var keyboardActionListener: KeyboardActionListener
    private var inputConnection: InputConnection? = null

    private var translateJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var btnBack: ImageButton
    private lateinit var btnSourceLang: TextView
    private lateinit var btnSwap: ImageButton
    private lateinit var btnTargetLang: TextView
    private lateinit var btnClear: ImageButton
    private lateinit var chipsContainer: LinearLayout
    private lateinit var cardContainer: LinearLayout
    private lateinit var etInput: EditText
    private lateinit var tvOutput: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var btnCopy: Button
    private lateinit var btnInsert: Button
    private lateinit var innerDivider: View

    private var currentSourceCode = "auto"
    private var currentTargetCode = "en"
    private var currentThemeColors: Colors? = null
    private val colors: Colors
        get() = currentThemeColors ?: Settings.getValues().mColors

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dpInt(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    data class Lang(val code: String, val name: String)

    companion object {
        val ALL_LANGUAGES = listOf(
            Lang("auto", "Авто"),
            Lang("ru", "Русский"),
            Lang("en", "English"),
            Lang("uk", "Українська"),
            Lang("kk", "Қазақша"),
            Lang("es", "Español"),
            Lang("de", "Deutsch"),
            Lang("fr", "Français"),
            Lang("it", "Italiano"),
            Lang("tr", "Türkçe"),
            Lang("zh-CN", "中文"),
            Lang("ja", "日本語"),
            Lang("ko", "한국어"),
            Lang("ar", "العربية"),
            Lang("pt", "Português"),
            Lang("pl", "Polski"),
            Lang("uz", "O'zbek"),
            Lang("tg", "Тоҷикӣ"),
            Lang("be", "Беларуская"),
            Lang("hy", "Հայերեն"),
            Lang("ka", "ქართული"),
            Lang("az", "Azərbaycan")
        )

        val QUICK_CHIPS = listOf(
            Lang("ru", "RU"),
            Lang("en", "EN"),
            Lang("uk", "UK"),
            Lang("kk", "KK"),
            Lang("es", "ES"),
            Lang("de", "DE"),
            Lang("fr", "FR"),
            Lang("tr", "TR"),
            Lang("zh-CN", "ZH"),
            Lang("ja", "JA"),
            Lang("ar", "AR")
        )
    }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.translate_view, this, true)
        setupViews()
    }

    private fun setupViews() {
        btnBack = findViewById(R.id.btn_translate_back)
        btnSourceLang = findViewById(R.id.btn_source_lang)
        btnSwap = findViewById(R.id.btn_swap_lang)
        btnTargetLang = findViewById(R.id.btn_target_lang)
        btnClear = findViewById(R.id.btn_clear_input)
        chipsContainer = findViewById(R.id.translate_chips_container)
        cardContainer = findViewById(R.id.translate_card_container)
        etInput = findViewById(R.id.et_translate_input)
        tvOutput = findViewById(R.id.tv_translate_output)
        pbLoading = findViewById(R.id.pb_translate_loading)
        btnCopy = findViewById(R.id.btn_translate_copy)
        btnInsert = findViewById(R.id.btn_translate_insert)
        innerDivider = findViewById(R.id.translate_inner_divider)

        btnBack.setOnClickListener {
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
        }

        btnSourceLang.setOnClickListener { showLanguagePicker(it, isSource = true) }
        btnTargetLang.setOnClickListener { showLanguagePicker(it, isSource = false) }

        btnSwap.setOnClickListener {
            if (currentSourceCode != "auto") {
                val temp = currentSourceCode
                currentSourceCode = currentTargetCode
                currentTargetCode = temp
                updateLanguageLabels()
                triggerTranslation(immediate = true)
            } else {
                Toast.makeText(context, "Выберите исходный язык для смены местами", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            etInput.setText("")
            tvOutput.text = ""
        }

        btnCopy.setOnClickListener {
            val text = tvOutput.text.toString()
            if (text.isNotBlank()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("LorKeys Translate", text))
                Toast.makeText(context, context.getString(R.string.translate_copied), Toast.LENGTH_SHORT).show()
            }
        }

        btnInsert.setOnClickListener {
            val text = tvOutput.text.toString()
            if (text.isNotBlank()) {
                inputConnection?.commitText(text, 1)
                KeyboardSwitcher.getInstance().setAlphabetKeyboard()
            }
        }

        setupQuickChips()

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                triggerTranslation(immediate = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateLanguageLabels()
    }

    private fun setupQuickChips() {
        chipsContainer.removeAllViews()
        for (chip in QUICK_CHIPS) {
            val tv = TextView(context).apply {
                text = chip.name
                textSize = 12f
                setPadding(dpInt(10f), dpInt(4f), dpInt(10f), dpInt(4f))
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dpInt(3f), 0, dpInt(3f), 0)
                }
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (currentTargetCode != chip.code) {
                        currentTargetCode = chip.code
                        updateLanguageLabels()
                        triggerTranslation(immediate = true)
                    }
                }
            }
            chipsContainer.addView(tv)
        }
    }

    private fun showLanguagePicker(anchor: View, isSource: Boolean) {
        val popup = PopupMenu(context, anchor)
        val list = if (isSource) ALL_LANGUAGES else ALL_LANGUAGES.filter { it.code != "auto" }
        list.forEachIndexed { index, lang ->
            popup.menu.add(0, index, index, lang.name)
        }
        popup.setOnMenuItemClickListener { item ->
            val selected = list.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            if (isSource) {
                currentSourceCode = selected.code
            } else {
                currentTargetCode = selected.code
            }
            updateLanguageLabels()
            triggerTranslation(immediate = true)
            true
        }
        popup.show()
    }

    private fun updateLanguageLabels() {
        btnSourceLang.text = ALL_LANGUAGES.firstOrNull { it.code == currentSourceCode }?.name ?: currentSourceCode
        btnTargetLang.text = ALL_LANGUAGES.firstOrNull { it.code == currentTargetCode }?.name ?: currentTargetCode
        updateQuickChipsHighlight()
    }

    private fun updateQuickChipsHighlight() {
        val accentColor = colors.get(ColorType.GESTURE_TRAIL)
        val keyBgColor = colors.get(ColorType.KEY_BACKGROUND)
        val keyTextColor = colors.get(ColorType.KEY_TEXT)

        for (i in 0 until chipsContainer.childCount) {
            val child = chipsContainer.getChildAt(i) as? TextView ?: continue
            val chip = QUICK_CHIPS.getOrNull(i) ?: continue
            val isSelected = chip.code == currentTargetCode
            val bg = GradientDrawable().apply {
                cornerRadius = dp(12f)
                if (isSelected) {
                    setColor(accentColor)
                } else {
                    setColor(keyBgColor)
                    setStroke(dpInt(1f), Color.argb(40, 128, 128, 128))
                }
            }
            child.background = bg
            child.setTextColor(if (isSelected) Color.WHITE else keyTextColor)
        }
    }

    private fun triggerTranslation(immediate: Boolean) {
        translateJob?.cancel()
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) {
            tvOutput.text = ""
            pbLoading.visibility = GONE
            return
        }

        pbLoading.visibility = VISIBLE
        translateJob = coroutineScope.launch {
            if (!immediate) {
                delay(400) // debounce typing
            }
            val result = executeGoogleTranslate(text, currentSourceCode, currentTargetCode)
            if (isActive) {
                pbLoading.visibility = GONE
                if (result != null) {
                    tvOutput.text = result
                } else {
                    tvOutput.text = context.getString(R.string.translate_error)
                }
            }
        }
    }

    private suspend fun executeGoogleTranslate(text: String, sl: String, tl: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val sentences = jsonArray.getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val part = sentences.getJSONArray(i).getString(0)
                    sb.append(part)
                }
                sb.toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun onOpen(connection: InputConnection?) {
        this.inputConnection = connection
        updateThemeColors(colors)

        // If user already had text selected in app, grab it automatically!
        var initialText = ""
        try {
            val selected = connection?.getSelectedText(0)?.toString()
            if (!selected.isNullOrBlank()) {
                initialText = selected
            }
        } catch (_: Exception) {}

        if (initialText.isNotEmpty()) {
            etInput.setText(initialText)
            etInput.setSelection(initialText.length)
            triggerTranslation(immediate = true)
        } else {
            if (etInput.text.isNotEmpty()) {
                triggerTranslation(immediate = true)
            }
        }
        etInput.requestFocus()
    }

    fun onClose() {
        translateJob?.cancel()
        inputConnection = null
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        setLayerType(if (enabled) LAYER_TYPE_HARDWARE else LAYER_TYPE_SOFTWARE, null)
    }

    fun updateThemeColors(colors: Colors) {
        currentThemeColors = colors
        val keyBgColor = colors.get(ColorType.KEY_BACKGROUND)
        val keyTextColor = colors.get(ColorType.KEY_TEXT)
        val accentColor = colors.get(ColorType.GESTURE_TRAIL)

        // Background
        setBackgroundColor(Color.TRANSPARENT)

        // Card container
        val cardBg = GradientDrawable().apply {
            cornerRadius = dp(14f)
            setColor(keyBgColor)
            setStroke(dpInt(1f), Color.argb(35, 128, 128, 128))
        }
        cardContainer.background = cardBg

        // Buttons
        val langBtnBg = GradientDrawable().apply {
            cornerRadius = dp(10f)
            setColor(keyBgColor)
            setStroke(dpInt(1f), Color.argb(45, 128, 128, 128))
        }
        btnSourceLang.background = langBtnBg
        btnSourceLang.setTextColor(keyTextColor)

        val targetBtnBg = GradientDrawable().apply {
            cornerRadius = dp(10f)
            setColor(keyBgColor)
            setStroke(dpInt(1f), Color.argb(45, 128, 128, 128))
        }
        btnTargetLang.background = targetBtnBg
        btnTargetLang.setTextColor(keyTextColor)

        // Icons tint
        btnBack.setColorFilter(keyTextColor)
        btnSwap.setColorFilter(keyTextColor)
        btnClear.setColorFilter(keyTextColor)

        // Text fields
        etInput.setTextColor(keyTextColor)
        etInput.setHintTextColor(Color.argb(120, Color.red(keyTextColor), Color.green(keyTextColor), Color.blue(keyTextColor)))
        tvOutput.setTextColor(keyTextColor)

        // Divider
        innerDivider.setBackgroundColor(Color.argb(35, Color.red(keyTextColor), Color.green(keyTextColor), Color.blue(keyTextColor)))

        // Action buttons
        val insertBg = GradientDrawable().apply {
            cornerRadius = dp(10f)
            setColor(accentColor)
        }
        btnInsert.background = insertBg
        btnInsert.setTextColor(Color.WHITE)

        val copyBg = GradientDrawable().apply {
            cornerRadius = dp(10f)
            setColor(keyBgColor)
            setStroke(dpInt(1f), Color.argb(50, 128, 128, 128))
        }
        btnCopy.background = copyBg
        btnCopy.setTextColor(keyTextColor)

        updateQuickChipsHighlight()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val settings = Settings.getValues()
        val abcHeight = ResourceUtils.getKeyboardHeight(resources, settings)
        val persistentEmojiEnabled = context.prefs().getBoolean(Settings.PREF_PERSISTENT_EMOJI_ROW, helium314.keyboard.latin.settings.Defaults.PREF_PERSISTENT_EMOJI_ROW)
        val emojiRowHeight = if (persistentEmojiEnabled) (41 * resources.displayMetrics.density).toInt() else 0
        val stripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val finalHeight = abcHeight + emojiRowHeight + stripHeight + paddingTop + paddingBottom + 1

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), finalHeight)
    }
}
