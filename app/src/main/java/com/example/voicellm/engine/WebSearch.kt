package com.example.voicellm.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WebSearch {

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        withTimeoutOrNull(8_000) {
            val result = tryDdgInstant(query)
                ?: tryWikipedia(query)
                ?: tryDdgHtml(query)
            result?.let { "Websuche für \"$query\":\n$it" } ?: ""
        } ?: ""
    }

    // ── DuckDuckGo Instant Answers – gut für Fakten, Definitionen, Berechnungen ──

    private fun tryDdgInstant(query: String): String? {
        return try {
            val enc  = URLEncoder.encode(query, "UTF-8")
            val json = get("https://api.duckduckgo.com/?q=$enc&format=json&no_html=1&skip_disambig=1") ?: return null
            val obj  = JSONObject(json)
            val parts = mutableListOf<String>()
            obj.optString("Answer").takeIf { it.isNotBlank() }?.let { parts += it }
            obj.optString("AbstractText").takeIf { it.isNotBlank() }?.let { parts += it }
            val topics = obj.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(3, topics.length())) {
                    topics.optJSONObject(i)?.optString("Text")
                        ?.takeIf { it.isNotBlank() }?.let { parts += it }
                }
            }
            parts.joinToString("\n").ifBlank { null }
        } catch (e: Exception) {
            Log.w("WebSearch", "DDG Instant fehlgeschlagen: ${e.message}")
            null
        }
    }

    // ── Wikipedia REST API – zuverlässig, kein Login, deutsch+englisch ──

    private fun tryWikipedia(query: String): String? {
        return try {
            // 1. Suche nach passendem Artikel
            val enc = URLEncoder.encode(query, "UTF-8")
            val searchJson = get(
                "https://de.wikipedia.org/w/api.php?action=query&list=search" +
                "&srsearch=$enc&format=json&srlimit=1&srprop=snippet"
            ) ?: return tryWikipediaEn(query)

            val searchArr = JSONObject(searchJson)
                .getJSONObject("query").getJSONArray("search")
            if (searchArr.length() == 0) return tryWikipediaEn(query)

            val title   = searchArr.getJSONObject(0).getString("title")
            val titleEnc = URLEncoder.encode(title, "UTF-8")

            // 2. Artikel-Zusammenfassung
            val summaryJson = get("https://de.wikipedia.org/api/rest_v1/page/summary/$titleEnc")
                ?: return null
            val extract = JSONObject(summaryJson).optString("extract").take(600)
            if (extract.isNotBlank()) "$title: $extract" else null
        } catch (e: Exception) {
            Log.w("WebSearch", "Wikipedia DE fehlgeschlagen: ${e.message}")
            tryWikipediaEn(query)
        }
    }

    private fun tryWikipediaEn(query: String): String? {
        return try {
            val enc = URLEncoder.encode(query, "UTF-8")
            val searchJson = get(
                "https://en.wikipedia.org/w/api.php?action=query&list=search" +
                "&srsearch=$enc&format=json&srlimit=1&srprop=snippet"
            ) ?: return null
            val searchArr = JSONObject(searchJson)
                .getJSONObject("query").getJSONArray("search")
            if (searchArr.length() == 0) return null
            val title    = searchArr.getJSONObject(0).getString("title")
            val titleEnc = URLEncoder.encode(title, "UTF-8")
            val summaryJson = get("https://en.wikipedia.org/api/rest_v1/page/summary/$titleEnc")
                ?: return null
            val extract = JSONObject(summaryJson).optString("extract").take(600)
            if (extract.isNotBlank()) "$title: $extract" else null
        } catch (e: Exception) {
            Log.w("WebSearch", "Wikipedia EN fehlgeschlagen: ${e.message}")
            null
        }
    }

    // ── DuckDuckGo HTML Scraping – Fallback für aktuelle/nicht-enzyklopädische Infos ──

    private fun tryDdgHtml(query: String): String? {
        return try {
            val enc  = URLEncoder.encode(query, "UTF-8")
            // POST an html.duckduckgo.com – liefert vollständige Suchergebnisse ohne JS
            val html = post("https://html.duckduckgo.com/html/", "q=$enc&kl=de-de")
                ?: get("https://lite.duckduckgo.com/lite/?q=$enc")
                ?: return null

            val snippets = mutableListOf<String>()
            // html.duckduckgo.com nutzt class="result__snippet"
            // lite.duckduckgo.com nutzt class="result-snippet"
            val rx = Regex(
                """class=["']result(?:__|-)snippet["'][^>]*>(.*?)<(?:/td|/div|span)""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            rx.findAll(html).take(4).forEach { m ->
                val text = m.groupValues[1]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&quot;", "\"")
                    .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")
                    .trim()
                if (text.length > 30) snippets += text
            }
            snippets.joinToString("\n").ifBlank { null }
        } catch (e: Exception) {
            Log.w("WebSearch", "DDG HTML fehlgeschlagen: ${e.message}")
            null
        }
    }

    // ── HTTP-Hilfsmethoden ──────────────────────────────────────────────────────

    private fun get(urlStr: String): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 4_000
            readTimeout    = 4_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            setRequestProperty("Accept-Language", "de,en;q=0.9")
            setRequestProperty("Accept", "application/json,text/html,*/*")
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }

    private fun post(urlStr: String, body: String): String? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout    = 4_000
            doOutput       = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8)")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept-Language", "de,en;q=0.9")
        }
        return try {
            conn.outputStream.write(body.toByteArray())
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }
}
