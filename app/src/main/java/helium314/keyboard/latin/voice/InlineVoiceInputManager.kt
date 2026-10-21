// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.dpToPx
import java.util.Locale

object InlineVoiceInputManager {
    private const val TAG = "InlineVoiceInput"
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    private var activeLatinIME: LatinIME? = null
    private var voiceBarView: View? = null
    private var statusTextView: TextView? = null
    private var micIconView: ImageView? = null

    fun toggleVoiceInput(latinIME: LatinIME) {
        if (isListening) {
            stopVoiceInput(latinIME)
        } else {
            startVoiceInput(latinIME)
        }
    }

    fun startVoiceInput(latinIME: LatinIME) {
        if (isListening) return
        activeLatinIME = latinIME

        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(latinIME, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = Intent(latinIME, VoicePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                latinIME.startActivity(intent)
            } catch (_: Exception) {
                RichInputMethodManager.getInstance().switchToShortcutIme(latinIME)
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(latinIME)) {
            Log.w(TAG, "SpeechRecognizer not available on device, falling back to shortcut IME")
            RichInputMethodManager.getInstance().switchToShortcutIme(latinIME)
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(latinIME)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(createListener(latinIME))

            val locale = RichInputMethodManager.getInstance().currentSubtypeLocale ?: Locale.getDefault()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toString())
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, locale.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            showVoiceBar(latinIME)
            recognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            stopVoiceInput(latinIME)
            RichInputMethodManager.getInstance().switchToShortcutIme(latinIME)
        }
    }

    fun stopVoiceInput(latinIME: LatinIME? = activeLatinIME) {
        isListening = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        hideVoiceBar(latinIME)
        activeLatinIME = null
    }

    private fun showVoiceBar(latinIME: LatinIME) {
        val strip = latinIME.suggestionStripView ?: return
        val colors = latinIME.settings.current.mColors
        val isNight = KeyboardTheme.isDarkThemeActive(latinIME)

        val bar = LinearLayout(latinIME).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(12.dpToPx(resources).toInt(), 2.dpToPx(resources).toInt(), 12.dpToPx(resources).toInt(), 2.dpToPx(resources).toInt())
        }

        val micBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val accentColor = colors.get(ColorType.SPECIAL_KEY_BACKGROUND)
            setColor(accentColor)
        }

        val mic = ImageView(latinIME).apply {
            layoutParams = LinearLayout.LayoutParams(34.dpToPx(resources).toInt(), 34.dpToPx(resources).toInt())
            background = micBg
            setImageResource(R.drawable.sym_keyboard_voice_holo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
            setPadding(6.dpToPx(resources).toInt(), 6.dpToPx(resources).toInt(), 6.dpToPx(resources).toInt(), 6.dpToPx(resources).toInt())
            setOnClickListener {
                stopVoiceInput(latinIME)
            }
        }
        micIconView = mic

        val statusText = TextView(latinIME).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dpToPx(resources).toInt()
                marginEnd = 12.dpToPx(resources).toInt()
            }
            text = latinIME.getString(R.string.voice_input_listening)
            textSize = 15f
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            KeyboardTypeface.applyToTextView(this)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusTextView = statusText

        val closeBtn = ImageView(latinIME).apply {
            layoutParams = LinearLayout.LayoutParams(30.dpToPx(resources).toInt(), 30.dpToPx(resources).toInt())
            setImageResource(R.drawable.ic_close)
            setColorFilter(colors.get(ColorType.KEY_TEXT))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener {
                stopVoiceInput(latinIME)
            }
        }

        bar.addView(mic)
        bar.addView(statusText)
        bar.addView(closeBtn)

        voiceBarView = bar
        strip.setExternalSuggestionView(bar, false)
    }

    private fun hideVoiceBar(latinIME: LatinIME?) {
        val ime = latinIME ?: return
        val strip = ime.suggestionStripView ?: return
        strip.setExternalSuggestionView(null, false)
        ime.setNeutralSuggestionStrip()
        voiceBarView = null
        statusTextView = null
        micIconView = null
    }

    private fun createListener(latinIME: LatinIME) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            statusTextView?.text = latinIME.getString(R.string.voice_input_listening)
        }

        override fun onBeginningOfSpeech() {
            statusTextView?.text = latinIME.getString(R.string.voice_input_listening)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.22f
            micIconView?.scaleX = scale
            micIconView?.scaleY = scale
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            statusTextView?.text = latinIME.getString(R.string.voice_input_processing)
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error code: $error")
            stopVoiceInput(latinIME)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                if (text.isNotEmpty()) {
                    val ic = latinIME.currentInputConnection
                    if (ic != null) {
                        ic.beginBatchEdit()
                        ic.commitText(text + " ", 1)
                        ic.endBatchEdit()
                    }
                }
            }
            stopVoiceInput(latinIME)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partial = matches[0]
                if (partial.isNotEmpty()) {
                    statusTextView?.text = partial
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
