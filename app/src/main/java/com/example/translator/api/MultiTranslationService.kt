package com.example.translator.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class TranslationProvider {
    ML_KIT,
    MY_MEMORY,
    LINGVA,
    SIMPLY_TRANSLATE
}

class MultiTranslationService {

    fun translate(
        text: String,
        from: String,
        to: String,
        provider: TranslationProvider,
        callback: (String) -> Unit
    ) {
        if (provider == TranslationProvider.ML_KIT) {
            // Handled by ScreenCaptureService directly for now
            return
        }

        Thread {
            try {
                val result = when (provider) {
                    TranslationProvider.MY_MEMORY -> translateMyMemory(text, from, to)
                    TranslationProvider.LINGVA -> translateLingva(text, from, to)
                    TranslationProvider.SIMPLY_TRANSLATE -> translateSimplyTranslate(text, from, to)
                    else -> "Unsupported provider"
                }
                callback(result)
            } catch (e: Exception) {
                callback("Error: ${e.message}")
            }
        }.start()
    }

    private fun translateMyMemory(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$encodedText&langpair=$from|$to")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.getJSONObject("responseData").getString("translatedText")
        } finally {
            connection.disconnect()
        }
    }

    private fun translateLingva(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://lingva.ml/api/v1/$from/$to/$encodedText")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.getString("translation")
        } finally {
            connection.disconnect()
        }
    }

    private fun translateSimplyTranslate(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        // SimplyTranslate often uses a GET endpoint for simple queries or POST for JSON
        // Using a common public instance pattern
        val url = URL("https://simplytranslate.org/api/translate?text=$encodedText&from=$from&to=$to&engine=google")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.getString("translated_text")
        } finally {
            connection.disconnect()
        }
    }
}