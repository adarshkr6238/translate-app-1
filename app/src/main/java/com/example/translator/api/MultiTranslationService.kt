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
     * Translates text using external APIs with automatic fallback.
     */
    suspend fun translate(
        text: String,
        from: String,
        to: String,
        provider: TranslationProvider
    ): String = withContext(Dispatchers.IO) {
        val providers = TranslationProvider.values().toMutableList()
        // Move selected provider to front
        providers.remove(provider)
        providers.add(0, provider)

        var lastError = ""
        for (current in providers) {
            try {
                val result = when (current) {
                    TranslationProvider.GOOGLE_FREE -> translateGoogleFree(text, from, to)
                    TranslationProvider.MY_MEMORY -> translateMyMemory(text, from, to)
                    TranslationProvider.LINGVA -> translateLingva(text, from, to)
                    TranslationProvider.SIMPLY_TRANSLATE -> translateSimplyTranslate(text, from, to)
                    TranslationProvider.LIBRE_TRANSLATE -> translateLibre(text, from, to)
                }
                if (!result.startsWith("Error:") && !result.contains("API Error: 429")) {
                    return@withContext result
                }
                lastError = result
            } catch (e: Exception) {
                lastError = "Error (${current.name}): ${e.message}"
            }
        }
        return@withContext "All providers failed. Last error: $lastError"
    }

    private fun translateGoogleFree(text: String, from: String, to: String): String {
        val src = if (from.equals("auto", true)) "auto" else from
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$src&tl=$to&dt=t&q=$encodedText")
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
                "API Error: ${connection.responseCode} (Google)"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun translateMyMemory(text: String, from: String, to: String): String {
        val src = if (from.equals("auto", true)) "Autodetect" else from
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$encodedText&langpair=$src|$to")
        return makeGetRequest(url) { json ->
            json.getJSONObject("responseData").getString("translatedText")
        }
    }

    private fun translateLingva(text: String, from: String, to: String): String {
        val src = if (from.equals("auto", true)) "auto" else from
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://lingva.ml/api/v1/$src/$to/$encodedText")
        return makeGetRequest(url) { json ->
            json.getString("translation")
        }
    }

    private fun translateSimplyTranslate(text: String, from: String, to: String): String {
        val src = if (from.equals("auto", true)) "auto" else from
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://simplytranslate.org/api/translate?text=$encodedText&from=$src&to=$to&engine=google")
        return makeGetRequest(url) { json ->
            json.getString("translated_text")
        }
    }

    private fun translateLibre(text: String, from: String, to: String): String {
        val src = if (from.equals("auto", true)) "auto" else from
        val url = URL("https://libretranslate.de/translate")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val body = JSONObject()
        body.put("q", text)
        body.put("source", src)
        body.put("target", to)
        body.put("format", "text")

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        return try {
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                JSONObject(response).getString("translatedText")
            } else {
                "API Error: ${connection.responseCode} (Libre)"
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