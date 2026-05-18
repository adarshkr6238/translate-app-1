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

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val languageIdentifier = LanguageIdentification.getClient()
    private val multiTranslationService = MultiTranslationService()
    
    // Default provider. Could be made configurable via UI.
    private var currentProvider = TranslationProvider.ML_KIT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenDensity = metrics.densityDpi
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        createNotificationChannel()
        startForeground(1, createNotification())
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
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Translator Active")
            .setContentText("Screen capture in progress")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun setupFloatingBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val diffX = event.rawX - initialTouchX
                        val diffY = event.rawY - initialTouchY
                        if (kotlin.math.abs(diffX) < 10 && kotlin.math.abs(diffY) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubbleView.setOnClickListener {
            captureScreen()
        }
        
        // Cycle provider on long click for demonstration
        bubbleView.setOnLongClickListener {
            currentProvider = when(currentProvider) {
                TranslationProvider.ML_KIT -> TranslationProvider.MY_MEMORY
                TranslationProvider.MY_MEMORY -> TranslationProvider.LINGVA
                TranslationProvider.LINGVA -> TranslationProvider.SIMPLY_TRANSLATE
                TranslationProvider.SIMPLY_TRANSLATE -> TranslationProvider.ML_KIT
            }
            updateResultUI("Provider: ${currentProvider.name}")
            true
        }

        windowManager.addView(bubbleView, params)
    }

    private fun captureScreen() {
        val image: Image? = imageReader?.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            processOCR(croppedBitmap)
        }
    }

    private fun processOCR(bitmap: Bitmap) {
        updateResultUI("Recognizing...")
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isNotBlank()) {
                    identifyLanguage(text)
                } else {
                    updateResultUI("No text found.")
                }
            }
            .addOnFailureListener { e ->
                updateResultUI("OCR Failed: ${e.message}")
            }
    }

    private fun identifyLanguage(text: String) {
        updateResultUI("Identifying Language...")
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und") {
                    if (currentProvider == TranslationProvider.ML_KIT) {
                        translateTextMLKit(text, languageCode)
                    } else {
                        translateTextOnline(text, languageCode, currentProvider)
                    }
                } else {
                    updateResultUI("Language unknown.")
                }
            }
            .addOnFailureListener { e ->
                updateResultUI("LangID Failed: ${e.message}")
            }
    }

    private fun translateTextMLKit(text: String, sourceLang: String) {
        if (sourceLang == "en") {
            updateResultUI(text)
            return
        }

        updateResultUI("Translating (ML Kit)...")
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(sourceLang) ?: TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        val translator = Translation.getClient(options)
        
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
            
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        updateResultUI(translatedText)
                        translator.close()
                    }
                    .addOnFailureListener { e ->
                        updateResultUI("ML Kit Failed: ${e.message}")
                        translator.close()
                    }
            }
            .addOnFailureListener { e ->
                updateResultUI("Model Download Failed: ${e.message}")
                translator.close()
            }
    }
    
    private fun translateTextOnline(text: String, sourceLang: String, provider: TranslationProvider) {
        updateResultUI("Translating (${provider.name})...")
        multiTranslationService.translate(text, sourceLang, "en", provider) { result ->
            updateResultUI(result)
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
        virtualDisplay?.release()
        mediaProjection?.stop()
        textRecognizer.close()
        languageIdentifier.close()
        if (::bubbleView.isInitialized) {
            windowManager.removeView(bubbleView)
        }
    }
}