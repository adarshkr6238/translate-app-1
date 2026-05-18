package com.example.translator

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.translator.api.TranslationProvider
import com.example.translator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager

    private val providers = TranslationProvider.values().map { it.name }
    private val languages = listOf("Auto", "en", "es", "fr", "de", "it", "ja", "ko", "zh", "ru")

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            saveSettings()
            startCaptureService(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setupSpinners()
        loadSettings()

        binding.btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun setupSpinners() {
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providers)
        binding.spinnerProvider.setAdapter(providerAdapter)

        val langAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        binding.spinnerSourceLang.setAdapter(langAdapter)
        
        val targetLangs = languages.subList(1, languages.size)
        val targetAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, targetLangs)
        binding.spinnerTargetLang.setAdapter(targetAdapter)
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.spinnerProvider.setText(prefs.getString("provider", TranslationProvider.ML_KIT.name), false)
        binding.spinnerSourceLang.setText(prefs.getString("sourceLang", "Auto"), false)
        binding.spinnerTargetLang.setText(prefs.getString("targetLang", "en"), false)
    }

    private fun saveSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().apply {
            putString("provider", binding.spinnerProvider.text.toString())
            putString("sourceLang", binding.spinnerSourceLang.text.toString())
            putString("targetLang", binding.spinnerTargetLang.text.toString())
            apply()
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA", data)
        }
        startForegroundService(intent)
        finish() // Close UI when bubble starts
    }
}
