package ru.fonar.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var torchOn = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var status: TextView
    private lateinit var toggleButton: Button
    private val permissionRequest = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        tts = TextToSpeech(this, this)
        buildInterface()
        if (cameraId == null) {
            toggleButton.isEnabled = false
            status.text = "На этом устройстве нет вспышки"
        }
    }

    private fun buildInterface() {
        val bg = Color.rgb(23, 25, 31)
        val yellow = Color.rgb(255, 213, 74)
        val muted = Color.rgb(170, 170, 170)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(bg)
        }
        layout.addView(TextView(this).apply {
            text = "Фонарь"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))

        layout.addView(View(this), LinearLayout.LayoutParams(1, dp(54)))
        layout.addView(TextView(this).apply {
            text = "ВКЛ / ВЫКЛ"
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        layout.addView(View(this), LinearLayout.LayoutParams(1, dp(18)))

        toggleButton = Button(this).apply {
            text = "⏻"
            textSize = 42f
            setTextColor(bg)
            setBackgroundColor(yellow)
            isAllCaps = false
            setOnClickListener { toggleTorch() }
        }
        val buttonParams = LinearLayout.LayoutParams(dp(150), dp(150))
        buttonParams.gravity = Gravity.CENTER
        layout.addView(toggleButton, buttonParams)
        layout.addView(View(this), LinearLayout.LayoutParams(1, dp(24)))

        status = TextView(this).apply {
            text = "Фонарик выключен"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        layout.addView(status, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)
    }

    private fun toggleTorch() {
        val id = cameraId ?: return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequest)
            return
        }
        try {
            val next = !torchOn
            cameraManager.setTorchMode(id, next)
            torchOn = next
            status.text = if (torchOn) "Фонарик включён" else "Фонарик выключен"
            toggleButton.text = if (torchOn) "●" else "⏻"
            if (torchOn) speakPhrase()
        } catch (_: Exception) {
            torchOn = false
            status.text = "Не удалось включить вспышку"
            Toast.makeText(this, "Проверьте доступ к камере и вспышке", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakPhrase() {
        if (ttsReady) {
            tts?.setPitch(1.15f)
            tts?.setSpeechRate(1.05f)
            tts?.speak("Ебать ты петух", TextToSpeech.QUEUE_FLUSH, null, "fonar_phrase")
        }
    }

    override fun onInit(code: Int) {
        if (code == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ru", "RU"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) runOnUiThread {
                Toast.makeText(this, "Установите русский голос в настройках синтеза речи", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            toggleTorch()
        } else if (requestCode == permissionRequest) {
            Toast.makeText(this, "Без разрешения камеры фонарик не включить", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (torchOn && cameraId != null) {
            try { cameraManager.setTorchMode(cameraId!!, false) } catch (_: Exception) {}
            torchOn = false
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
