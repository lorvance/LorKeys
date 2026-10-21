// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.isDarkColor
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.ArrayList
import java.util.Locale
import kotlin.math.pow

object CalculatorHelper {

    private val MATH_PATTERN = Regex("""(?:^|[\s\n=;:,(])(\(?-?\d+(?:[.,]\d+)?(?:(?:\s*[\+\-\*\/×÷\^%]\s*\(?-?\d+(?:[.,]\d+)?\)?)+))\s*(=)?$""")

    data class CalculationResult(val expression: String, val resultString: String, val endsWithEquals: Boolean)

    var isCalculatorExternalSuggestionVisible: Boolean = false
        private set

    fun evaluate(text: CharSequence?): CalculationResult? {
        if (text.isNullOrBlank()) return null
        val match = MATH_PATTERN.find(text.trimEnd()) ?: return null
        val expr = match.groupValues[1].trim()
        val endsWithEquals = match.groupValues[2] == "="

        if (!expr.contains(Regex("""[\+\-\*\/×÷\^%]"""))) return null

        val resultVal = evaluateExpression(expr) ?: return null
        val formatted = formatNumber(resultVal)
        if (formatted.isEmpty()) return null
        return CalculationResult(expr, formatted, endsWithEquals)
    }

    fun tryShowCalculatorSuggestion(latinIME: LatinIME): Boolean {
        if (!latinIME.settings.current.mInlineCalculator) {
            clearIfVisible(latinIME)
            return false
        }
        val strip = latinIME.suggestionStripView ?: return false
        val ic = latinIME.currentInputConnection
        val textBefore = ic?.getTextBeforeCursor(40, 0)?.toString()
        val calc = evaluate(textBefore)
        if (calc == null) {
            clearIfVisible(latinIME)
            return false
        }

        try {
            val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), strip, false)
            binding.clipboardSuggestionPreview.isGone = true
            binding.clipboardSuggestionClose.isGone = true
            val textView = binding.clipboardSuggestionText
            KeyboardTypeface.applyToTextView(textView)

            val isNight = ResourceUtils.isNight(latinIME.resources) || KeyboardTheme.isDarkThemeActive(latinIME)
            val colors = latinIME.settings.current.mColors
            val keyBg = colors.get(ColorType.KEY_BACKGROUND)
            val pillBgColor = if (isNight) {
                if (isDarkColor(keyBg)) ColorUtils.blendARGB(keyBg, Color.WHITE, 0.16f) else 0xFF2C2D30.toInt()
            } else {
                if (!isDarkColor(keyBg)) ColorUtils.blendARGB(keyBg, Color.BLACK, 0.08f) else 0xFFE9EAEC.toInt()
            }
            val accentColor = colors.get(ColorType.SPECIAL_KEY_BACKGROUND)

            val pillDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24.dpToPx(latinIME.resources).toFloat()
                setColor(pillBgColor)
                setStroke(1.dpToPx(latinIME.resources).toInt(), ColorUtils.setAlphaComponent(accentColor, 0x99))
            }
            binding.root.background = pillDrawable
            val margin = 4.dpToPx(latinIME.resources).toInt()
            binding.root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(margin, margin, margin, margin)
            }

            val textColor = if (isNight) Color.WHITE else Color.BLACK
            textView.text = "= " + calc.resultString
            textView.setTextColor(textColor)
            val padH = 16.dpToPx(latinIME.resources).toInt()
            val padV = 6.dpToPx(latinIME.resources).toInt()
            textView.setPadding(padH, padV, padH, padV)
            textView.textSize = 15f

            textView.setOnClickListener {
                commitCalculatorResult(latinIME, calc)
                clearIfVisible(latinIME)
            }

            isCalculatorExternalSuggestionVisible = true
            strip.setExternalSuggestionView(binding.root, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun clearIfVisible(latinIME: LatinIME) {
        if (isCalculatorExternalSuggestionVisible) {
            isCalculatorExternalSuggestionVisible = false
            latinIME.suggestionStripView?.setExternalSuggestionView(null, false)
        }
    }

    fun injectCalculatorSuggestion(latinIME: LatinIME, originalWords: SuggestedWords): SuggestedWords {
        val ic = latinIME.currentInputConnection ?: return originalWords
        val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: return originalWords
        val calc = evaluate(textBefore) ?: return originalWords

        val displayWord = "= " + calc.resultString
        val calcInfo = SuggestedWordInfo(
            displayWord,
            "",
            SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_HARDCODED,
            Dictionary.DICTIONARY_APPLICATION_DEFINED,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE
        )

        val newList = ArrayList<SuggestedWordInfo>()
        newList.add(calcInfo)
        for (i in 0 until originalWords.size()) {
            val info = originalWords.getInfo(i)
            if (info.mWord != displayWord) {
                newList.add(info)
            }
        }

        return SuggestedWords(
            newList,
            originalWords.mRawSuggestions,
            calcInfo,
            true,
            false,
            false,
            SuggestedWords.INPUT_STYLE_PREDICTION,
            originalWords.mSequenceNumber
        )
    }

    fun commitCalculatorResult(latinIME: LatinIME, calc: CalculationResult) {
        val ic = latinIME.currentInputConnection ?: return
        val textToCommit = if (calc.endsWithEquals) calc.resultString else " = " + calc.resultString
        ic.beginBatchEdit()
        ic.commitText(textToCommit, 1)
        ic.endBatchEdit()
    }

    fun commitCalculatorResult(latinIME: LatinIME, info: SuggestedWordInfo) {
        val ic = latinIME.currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val calc = evaluate(textBefore)
        if (calc != null) {
            commitCalculatorResult(latinIME, calc)
        } else {
            val textToCommit = info.mWord.removePrefix("= ")
            ic.beginBatchEdit()
            ic.commitText(textToCommit, 1)
            ic.endBatchEdit()
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#,##0.######", symbols).apply {
            isGroupingUsed = false
        }
        return df.format(value)
    }

    private fun evaluateExpression(expr: String): Double? {
        return try {
            val normalized = expr
                .replace("×", "*")
                .replace("÷", "/")
                .replace(",", ".")
            Parser(normalized).parse()
        } catch (_: Exception) {
            null
        }
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw RuntimeException("Unexpected end: " + ch.toChar())
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Div by 0")
                        x /= divisor
                    }
                    eat('%'.code) -> x /= 100.0
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else {
                throw RuntimeException("Unexpected char: " + ch.toChar())
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            if (eat('%'.code)) x /= 100.0

            return x
        }
    }
}
