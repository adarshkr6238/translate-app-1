package com.example.translator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class TranslationProvider {
    GOOGLE_FREE,
    MY_MEMORY,
    LINGVA,
    SIMPLY_TRANSLATE,
    LIBRE_TRANSLATE
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
        return@withContext try {
            when (provider) {
                TranslationProvider.GOOGLE_FREE -> translateGoogleFree(text, from, to)
                TranslationProvider.MY_MEMORY -> translateMyMemory(text, from, to)
                TranslationProvider.LINGVA -> translateLingva(text, from, to)
                TranslationProvider.SIMPLY_TRANSLATE -> translateSimplyTranslate(text, from, to)
                TranslationProvider.LIBRE_TRANSLATE -> translateLibre(text, from, to)
                else -> "Unsupported provider"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun translateGoogleFree(text: String, from: String, to: String): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$from&tl=$to&dt=t&q=$encodedText")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonArray = org.json.JSONArray(response)
                val sentences = jsonArray.getJSONArray(0)
                val result = StringBuilder()
                for (i in 0 until sentences.length()) {
                    result.append(sentences.getJSONArray(i).getString(0))
                }
                result.toString()
            } else {
                "Google API Error: ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
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

    private fun translateLibre(text: String, from: String, to: String): String {
        val url = URL("https://libretranslate.de/translate")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val body = JSONObject()
        body.put("q", text)
        body.put("source", from)
        body.put("target", to)
        body.put("format", "text")

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                JSONObject(response).getString("translatedText")
            } else {
                "Libre API Error: ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
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