#!/bin/bash
set -e
cd /workspaces/translate-app-1

# 1. Update build.gradle to add preferences and ensure it builds
cat << 'EOF' > app/build.gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.translator'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.translator"
        minSdk 24
        targetSdk 34
        versionCode 4
        versionName "2.0.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.preference:preference-ktx:1.2.1'

    // ML Kit Dependencies (Unbundled)
    implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.0'
    implementation 'com.google.android.gms:play-services-mlkit-language-id:17.0.0'
    implementation 'com.google.mlkit:translate:17.0.2'

    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
EOF

# 2. Material Themes & Colors
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/values-night

cat << 'EOF' > app/src/main/res/values/colors.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#6200EE</color>
    <color name="primary_variant">#3700B3</color>
    <color name="secondary">#03DAC5</color>
    <color name="background">#F5F5F5</color>
    <color name="surface">#FFFFFF</color>
    <color name="error">#B00020</color>
    <color name="on_primary">#FFFFFF</color>
    <color name="on_secondary">#000000</color>
    <color name="on_background">#000000</color>
    <color name="on_surface">#000000</color>
    <color name="bubble_bg">#CC000000</color>
</resources>
EOF

cat << 'EOF' > app/src/main/res/values-night/colors.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#BB86FC</color>
    <color name="primary_variant">#3700B3</color>
    <color name="secondary">#03DAC5</color>
    <color name="background">#121212</color>
    <color name="surface">#1E1E1E</color>
    <color name="error">#CF6679</color>
    <color name="on_primary">#000000</color>
    <color name="on_secondary">#000000</color>
    <color name="on_background">#FFFFFF</color>
    <color name="on_surface">#FFFFFF</color>
    <color name="bubble_bg">#E6000000</color>
</resources>
EOF

cat << 'EOF' > app/src/main/res/values/themes.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.TranslateApp1" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_variant</item>
        <item name="colorOnPrimary">@color/on_primary</item>
        <item name="colorSecondary">@color/secondary</item>
        <item name="colorOnSecondary">@color/on_secondary</item>
        <item name="android:colorBackground">@color/background</item>
        <item name="colorOnBackground">@color/on_background</item>
        <item name="colorSurface">@color/surface</item>
        <item name="colorOnSurface">@color/on_surface</item>
    </style>
</resources>
EOF

cat << 'EOF' > app/src/main/res/values/strings.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Translator</string>
</resources>
EOF

# 3. Main Activity Layout (Clean UI)
cat << 'EOF' > app/src/main/res/layout/activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <TextView
        android:id="@+id/tvTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Floating Translator Settings"
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:layout_marginTop="16dp" />

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/providerLayout"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Translation Provider"
        android:layout_marginTop="32dp"
        app:layout_constraintTop_toBottomOf="@id/tvTitle">
        <AutoCompleteTextView
            android:id="@+id/spinnerProvider"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="none" />
    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/sourceLangLayout"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Source Language (Force)"
        android:layout_marginTop="16dp"
        app:layout_constraintTop_toBottomOf="@id/providerLayout">
        <AutoCompleteTextView
            android:id="@+id/spinnerSourceLang"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="none" />
    </com.google.android.material.textfield.TextInputLayout>

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/targetLangLayout"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Target Language"
        android:layout_marginTop="16dp"
        app:layout_constraintTop_toBottomOf="@id/sourceLangLayout">
        <AutoCompleteTextView
            android:id="@+id/spinnerTargetLang"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="none" />
    </com.google.android.material.textfield.TextInputLayout>

    <Button
        android:id="@+id/btnStart"
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:text="Start Bubble"
        android:textSize="18sp"
        android:layout_marginTop="48dp"
        app:layout_constraintTop_toBottomOf="@id/targetLangLayout" />

</androidx.constraintlayout.widget.ConstraintLayout>
EOF

# 4. Bubble Layout (Update colors)
cat << 'EOF' > app/src/main/res/layout/bubble_layout.xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bubble_root"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@android:color/transparent">

    <ImageView
        android:id="@+id/bubble_icon"
        android:layout_width="60dp"
        android:layout_height="60dp"
        android:src="@android:drawable/ic_menu_search"
        android:background="@drawable/circle_bg"
        android:padding="12dp"
        android:elevation="4dp"
        android:contentDescription="Translator Bubble" />

    <TextView
        android:id="@+id/txtResult"
        android:layout_width="250dp"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:background="@color/bubble_bg"
        android:padding="8dp"
        android:text=""
        android:textColor="@color/on_primary"
        android:visibility="gone"
        android:elevation="6dp" />

</FrameLayout>
EOF

# Create circle drawable for bubble
cat << 'EOF' > app/src/main/res/drawable/circle_bg.xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/primary"/>
</shape>
EOF

# 5. MainActivity Kotlin
cat << 'EOF' > app/src/main/java/com/example/translator/MainActivity.kt
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
        binding.spinnerTargetLang.setAdapter(langAdapter.subList(1, languages.size)) // No 'Auto' for target
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
EOF

# 6. ScreenCaptureService Kotlin (Update to read prefs & handle multiple text blocks)
cat << 'EOF' > app/src/main/java/com/example/translator/ScreenCaptureService.kt
package com.example.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.example.translator.api.MultiTranslationService
import com.example.translator.api.TranslationProvider
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private val CHANNEL_ID = "ScreenCaptureServiceChannel"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenDensity: Int = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()
    private val multiTranslationService = MultiTranslationService()
    
    private var currentProvider = TranslationProvider.ML_KIT
    private var sourceLangSetting = "Auto"
    private var targetLangSetting = "en"
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenDensity = metrics.densityDpi
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        
        loadSettings()

        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val provStr = prefs.getString("provider", "ML_KIT") ?: "ML_KIT"
        currentProvider = try { TranslationProvider.valueOf(provStr) } catch(e:Exception){ TranslationProvider.ML_KIT }
        sourceLangSetting = prefs.getString("sourceLang", "Auto") ?: "Auto"
        targetLangSetting = prefs.getString("targetLang", "en") ?: "en"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode == Activity.RESULT_OK && data != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            setupVirtualDisplay()
        }

        setupFloatingBubble()
        return START_NOT_STICKY
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Screen Capture Service Channel", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Translator Active (${currentProvider.name})")
            .setContentText("Tap bubble to translate. Source: $sourceLangSetting")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupFloatingBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 100
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(event.rawX - initialTouchX) < 10 && kotlin.math.abs(event.rawY - initialTouchY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleView.setOnClickListener { if (!isProcessing) captureScreen() }
        windowManager.addView(bubbleView, params)
    }

    private fun captureScreen() {
        val image: Image? = imageReader?.acquireLatestImage()
        if (image != null) {
            isProcessing = true
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowPadding = planes[0].rowStride - pixelStride * screenWidth
            val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()
            processOCR(croppedBitmap)
        }
    }

    private fun processOCR(bitmap: Bitmap) {
        updateResultUI("Recognizing...")
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                bitmap.recycle()
                // Process text block by block to handle mixed languages better if Auto, or just combine if forced
                val text = visionText.textBlocks.joinToString("\n") { it.text }
                if (text.isNotBlank()) {
                    if (sourceLangSetting != "Auto") {
                        // Force source language (Fixes multi-lang confusion)
                        translateBasedOnProvider(text, sourceLangSetting)
                    } else {
                        identifyLanguage(text)
                    }
                } else {
                    updateResultUI("No text found.")
                    isProcessing = false
                }
            }
            .addOnFailureListener { e ->
                bitmap.recycle(); updateResultUI("OCR Failed: ${e.message}"); isProcessing = false
            }
    }

    private fun identifyLanguage(text: String) {
        updateResultUI("Identifying Language...")
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und") {
                    translateBasedOnProvider(text, languageCode)
                } else {
                    updateResultUI("Language unknown. Try forcing Source Lang.")
                    isProcessing = false
                }
            }
            .addOnFailureListener { e ->
                updateResultUI("LangID Failed: ${e.message}"); isProcessing = false
            }
    }
    
    private fun translateBasedOnProvider(text: String, sourceLang: String) {
        if (currentProvider == TranslationProvider.ML_KIT) {
            translateTextMLKit(text, sourceLang)
        } else {
            translateTextOnline(text, sourceLang, currentProvider)
        }
    }

    private fun translateTextMLKit(text: String, sourceLang: String) {
        if (sourceLang == targetLangSetting) {
            updateResultUI(text); isProcessing = false; return
        }
        updateResultUI("Translating (ML Kit)...")
        val src = TranslateLanguage.fromLanguageTag(sourceLang) ?: TranslateLanguage.ENGLISH
        val tgt = TranslateLanguage.fromLanguageTag(targetLangSetting) ?: TranslateLanguage.ENGLISH
        val options = TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().requireWifi().build()
            
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        updateResultUI(translatedText); translator.close(); isProcessing = false
                    }
                    .addOnFailureListener { e ->
                        updateResultUI("ML Kit Failed: ${e.message}"); translator.close(); isProcessing = false
                    }
            }
            .addOnFailureListener { e ->
                updateResultUI("Model Download Failed: ${e.message}"); translator.close(); isProcessing = false
            }
    }
    
    private fun translateTextOnline(text: String, sourceLang: String, provider: TranslationProvider) {
        updateResultUI("Translating (${provider.name})...")
        serviceScope.launch {
            val result = multiTranslationService.translate(text, sourceLang, targetLangSetting, provider)
            updateResultUI(result)
            isProcessing = false
        }
    }

    private fun updateResultUI(result: String) {
        mainHandler.post {
            bubbleView.findViewById<TextView>(R.id.txtResult).apply {
                visibility = View.VISIBLE
                text = result
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel(); virtualDisplay?.release(); mediaProjection?.stop()
        textRecognizer.close(); languageIdentifier.close()
        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
    }
}
EOF

# Ensure icons exist for build
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi

for dir in app/src/main/res/mipmap-*; do
  touch $dir/ic_launcher.png
  touch $dir/ic_launcher_round.png
done

echo UI update applied.
