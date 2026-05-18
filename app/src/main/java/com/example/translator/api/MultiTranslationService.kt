package com.example.translator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * Translates text using external APIs.
     * Optimized to use Coroutines instead of manual threads.
     */
    suspend fun translate(
        text: String,
        from: String,
        to: String,
        provider: TranslationProvider
    ): String = withContext(Dispatchers.IO) {
        if (provider == TranslationProvider.ML_KIT) return@withContext "Use ML Kit locally"

        return@withContext try {
            when (provider) {
                TranslationProvider.MY_MEMORY -> translateMyMemory(text, from, to)
                TranslationProvider.LINGVA -> translateLingva(text, from, to)
                TranslationProvider.SIMPLY_TRANSLATE -> translateSimplyTranslate(text, from, to)
                else -> "Unsupported provider"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun translateMyMemory(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$encodedText&langpair=$from|$to")
        return makeGetRequest(url) { json ->
            json.getJSONObject("responseData").getString("translatedText")
        }
    }

    private fun translateLingva(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://lingva.ml/api/v1/$from/$to/$encodedText")
        return makeGetRequest(url) { json ->
            json.getString("translation")
        }
    }

    private fun translateSimplyTranslate(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://simplytranslate.org/api/translate?text=$encodedText&from=$from&to=$to&engine=google")
        return makeGetRequest(url) { json ->
            json.getString("translated_text")
        }
    }

    private fun makeGetRequest(url: URL, parser: (JSONObject) -> String): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parser(JSONObject(response))
            } else {
                "API Error: ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }
}