package com.example.voicellm.engine

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val TAG = "ModelDownloader"

    data class Progress(
        val phase: String,       // "download" | "extract"
        val bytes: Long,
        val total: Long,         // -1 = unbekannt
        val done: Boolean,
        val error: String? = null,
    )

    // -------------------------------------------------------------------------
    // Direkter Datei-Download (fuer LLM .task)
    //
    // Behandelt HuggingFace-Redirects korrekt: Auth-Header wird NUR an
    // huggingface.co gesendet, nicht an CDN-/S3-Weiterleitungen.
    // Nach dem Download wird die Datei auf ein gueltiges ZIP-Archiv geprueft
    // (.task-Dateien sind ZIP-Archive).
    // -------------------------------------------------------------------------
    fun download(url: String, dest: File, bearerToken: String? = null): Flow<Progress> = flow {
        val partial = File("${dest.absolutePath}.partial")
        try {
            dest.parentFile?.mkdirs()
            // Bereits heruntergeladene Bytes wiederverwenden (HTTP Range Resume)
            val resumeFrom = if (partial.exists()) partial.length() else 0L
            if (resumeFrom > 0) Log.i(TAG, "Resume ${dest.name} ab $resumeFrom Bytes")
            emit(Progress("download", resumeFrom, -1, false))

            val (inputStream, total, isResume) = openWithRedirects(
                url, bearerToken, rangeStart = resumeFrom)

            // Bei 206 Partial Content: an bestehende Datei anhaengen
            val outFile = if (isResume) FileOutputStream(partial, true)
                          else partial.outputStream()

            var bytesTotal = if (isResume) resumeFrom else 0L
            val reportedTotal = if (total > 0 && isResume) total + resumeFrom else total

            inputStream.use { input ->
                outFile.use { out ->
                    val buf = ByteArray(128 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        bytesTotal += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 500) {
                            emit(Progress("download", bytesTotal, reportedTotal, false))
                            lastEmit = now
                        }
                    }
                }
            }

            // Validate: .task files are ZIP archives (magic bytes PK\x03\x04)
            val err = validateZip(partial, dest.name)
            if (err != null) {
                partial.delete()
                emit(Progress("download", 0, -1, true, err))
                return@flow
            }

            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) { partial.copyTo(dest, overwrite = true); partial.delete() }
            emit(Progress("download", dest.length(), total, true))
            Log.i(TAG, "Download OK: ${dest.name}  ${dest.length()} bytes")

        } catch (ce: CancellationException) {
            partial.delete()
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Download error", t)
            partial.delete()
            emit(Progress("download", 0, -1, true, t.message ?: t.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------------------------------------------------------
    // Download + tar.bz2-Extraktion (fuer TTS Piper-Modell)
    // -------------------------------------------------------------------------
    fun downloadAndExtract(
        url: String,
        destDir: File,
        bearerToken: String? = null,
    ): Flow<Progress> = flow {
        val archiveName = url.substringAfterLast('/')
        val tmpArchive  = File(destDir.parentFile ?: destDir, "$archiveName.partial")

        try {
            destDir.parentFile?.mkdirs()
            tmpArchive.delete()
            emit(Progress("download", 0, -1, false))

            val resumeFrom = if (tmpArchive.exists()) tmpArchive.length() else 0L
            if (resumeFrom > 0) Log.i(TAG, "Resume Archiv ab $resumeFrom Bytes")
            val (inputStream, rawTotal, isResume) = openWithRedirects(url, bearerToken,
                rangeStart = resumeFrom)
            val total = if (rawTotal > 0 && isResume) rawTotal + resumeFrom else rawTotal

            val archiveOut = if (isResume) FileOutputStream(tmpArchive, true)
                             else tmpArchive.outputStream()
            var bytesTotal = if (isResume) resumeFrom else 0L

            inputStream.use { input ->
                archiveOut.use { out ->
                    val buf = ByteArray(128 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        bytesTotal += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 500) {
                            emit(Progress("download", bytesTotal, total, false))
                            lastEmit = now
                        }
                    }
                }
            }
            emit(Progress("download", tmpArchive.length(), total, false))

            // Extraktion
            Log.i(TAG, "Extracting $archiveName -> $destDir")
            emit(Progress("extract", 0, -1, false))
            destDir.deleteRecursively()
            destDir.mkdirs()

            BZip2CompressorInputStream(tmpArchive.inputStream().buffered()).use { bz2 ->
                TarArchiveInputStream(bz2).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        val stripped = entry.name.substringAfter('/').trimStart('/')
                        if (stripped.isEmpty()) { entry = tar.nextEntry; continue }

                        val destFile = File(destDir, stripped)
                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered().use { out -> tar.copyTo(out) }
                        }
                        entry = tar.nextEntry
                    }
                }
            }

            // Piper-Konvention: erstes .onnx im Root-Dir -> "model.onnx"
            val onnxFiles = destDir.listFiles { f -> f.isFile && f.extension == "onnx" }
            if (!onnxFiles.isNullOrEmpty()) {
                val target = File(destDir, "model.onnx")
                if (!target.exists()) onnxFiles.first().renameTo(target)
            }

            tmpArchive.delete()
            emit(Progress("extract", 0, -1, true))

        } catch (ce: CancellationException) {
            tmpArchive.delete()
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Extract error", t)
            tmpArchive.delete()
            emit(Progress("extract", 0, -1, true, t.message ?: t.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------------------------------------------------------
    // Hilfsfunktionen
    // -------------------------------------------------------------------------

    data class ConnResult(val stream: InputStream, val contentLength: Long, val isResume: Boolean)

    /**
     * Oeffnet eine HTTP-Verbindung mit manuellem Redirect-Handling.
     * Auth-Header nur an Original-Host (nicht CDN).
     * [rangeStart] > 0: sendet "Range: bytes=X-" fuer Resume; liefert isResume=true bei HTTP 206.
     */
    private fun openWithRedirects(
        startUrl: String,
        bearerToken: String?,
        rangeStart: Long = 0L,
        maxRedirects: Int = 8,
    ): ConnResult {
        var url = startUrl
        val originalHost = URL(startUrl).host

        repeat(maxRedirects + 1) {
            val sendAuth = bearerToken != null && URL(url).host == originalHost
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout    = 120_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "VoiceLLM-Android/1.0")
                if (sendAuth) setRequestProperty("Authorization", "Bearer $bearerToken")
                if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            conn.connect()
            val code = conn.responseCode

            if (code in 301..303 || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IllegalStateException("Redirect ohne Location-Header")
                url = if (location.startsWith("http")) location
                      else "${URL(url).protocol}://${URL(url).host}$location"
                Log.d(TAG, "Redirect $code -> $url")
                return@repeat
            }
            if (code == 206) {
                // Partial Content – Server unterstuetzt Resume
                return ConnResult(conn.inputStream, conn.contentLengthLong, isResume = true)
            }
            if (code in 200..299) {
                // Kein Range-Support – von vorne anfangen
                return ConnResult(conn.inputStream, conn.contentLengthLong, isResume = false)
            }
            val body = runCatching {
                conn.errorStream?.bufferedReader()?.readText()?.take(300) ?: ""
            }.getOrDefault("")
            conn.disconnect()
            val hint = when (code) {
                401 -> " (Token ungueltig oder fehlt)"
                403 -> " (Kein Zugriff)"
                404 -> " (Datei nicht gefunden)"
                else -> ""
            }
            throw IllegalStateException("HTTP $code$hint  $body".trimEnd())
        }
        throw IllegalStateException("Zu viele Redirects (>$maxRedirects)")
    }

    /**
     * Prueft, ob [file] eine HTML-Fehlerseite ist (Server-Fehler statt Modelldatei).
     * Liest nur die ersten 512 Bytes – kein OOM bei grossen Dateien.
     * Gibt null zurueck wenn OK, sonst eine Fehlermeldung.
     */
    private fun validateZip(file: File, label: String): String? {
        if (file.length() < 4) {
            return "$label: Datei zu klein (${file.length()} Bytes) – Download unvollstaendig?"
        }
        val header = ByteArray(512)
        val read = file.inputStream().use { it.read(header) }
        val preview = header.copyOf(read).toString(Charsets.ISO_8859_1)
        // HTML-Fehlerseiten beginnen mit '<' oder '{'
        if (preview.trimStart().let { it.startsWith("<") || it.startsWith("{\"") }) {
            Log.w(TAG, "Server returned error page for $label: ${preview.take(200)}")
            return "$label: Server hat keine Modelldatei geliefert.\n" +
                "Moegliche Ursache: Netzwerkfehler oder falsche URL.\n" +
                preview.take(120)
        }
        return null
    }
}
