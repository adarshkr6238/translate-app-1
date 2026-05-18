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
import android.view.MotionEvent
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
import kotlin.math.abs

class ScreenCaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private var selectionBoxView: View? = null
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
    private var regionMode = false
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
        regionMode = prefs.getBoolean("regionMode", false)
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
        if (regionMode) {
            setupSelectionBox()
        }
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
            .setContentText("Tap bubble to translate. Region Mode: $regionMode")
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
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleView.setOnClickListener {
            val txtResult = bubbleView.findViewById<TextView>(R.id.txtResult)
            if (txtResult.visibility == View.VISIBLE) {
                txtResult.visibility = View.GONE
                txtResult.text = ""
            } else {
                if (!isProcessing) captureScreen()
            }
        }
        windowManager.addView(bubbleView, params)
    }

    private fun setupSelectionBox() {
        selectionBoxView = LayoutInflater.from(this).inflate(R.layout.selection_box_layout, null)
        val params = WindowManager.LayoutParams(
            200, 200,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 4; y = screenHeight / 4
        }

        val selectionBox = selectionBoxView!!.findViewById<View>(R.id.selection_box)
        val resizeHandle = selectionBoxView!!.findViewById<View>(R.id.resize_handle)

        selectionBox.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(selectionBoxView, params)
                        return true
                    }
                }
                return false
            }
        })

        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0; private var initialHeight = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = params.width; initialHeight = params.height
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = (initialWidth + (event.rawX - initialTouchX).toInt()).coerceAtLeast(100)
                        params.height = (initialHeight + (event.rawY - initialTouchY).toInt()).coerceAtLeast(100)
                        selectionBox.layoutParams.width = params.width
                        selectionBox.layoutParams.height = params.height
                        windowManager.updateViewLayout(selectionBoxView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(selectionBoxView, params)
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
            
            var finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()

            if (regionMode && selectionBoxView != null) {
                val params = selectionBoxView!!.layoutParams as WindowManager.LayoutParams
                val x = params.x.coerceIn(0, screenWidth - 100)
                val y = params.y.coerceIn(0, screenHeight - 100)
                val w = params.width.coerceAtMost(screenWidth - x)
                val h = params.height.coerceAtMost(screenHeight - y)
                val regionBitmap = Bitmap.createBitmap(finalBitmap, x, y, w, h)
                finalBitmap.recycle()
                finalBitmap = regionBitmap
            }

            processOCR(finalBitmap)
        }
    }

    private fun processOCR(bitmap: Bitmap) {
        updateResultUI("Recognizing...")
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                bitmap.recycle()
                val text = visionText.textBlocks.joinToString("\n") { it.text }
                if (text.isNotBlank()) {
                    if (sourceLangSetting != "Auto") {
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
        if (selectionBoxView != null) windowManager.removeView(selectionBoxView)
    }
}
