package helium314.keyboard.latin.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import helium314.keyboard.keyboard.KeyboardSwitcher

class VoicePermissionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            KeyboardSwitcher.getInstance().latinIME?.let {
                InlineVoiceInputManager.startVoiceInput(it)
            }
            return
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_AUDIO)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            KeyboardSwitcher.getInstance().latinIME?.let {
                InlineVoiceInputManager.startVoiceInput(it)
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE_AUDIO = 2002
    }
}
