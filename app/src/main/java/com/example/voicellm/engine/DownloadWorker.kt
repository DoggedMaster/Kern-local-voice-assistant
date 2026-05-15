package com.example.voicellm.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createChannel(applicationContext)
        val type    = inputData.getString(KEY_TYPE) ?: TYPE_DIRECT
        val notifId = if (type == TYPE_EXTRACT) NOTIF_ID_TTS else NOTIF_ID_LLM
        return buildForegroundInfo("Vorbereiten …", -1f, notifId)
    }

    override suspend fun doWork(): Result {
        createChannel(applicationContext)

        val url  = inputData.getString(KEY_URL)  ?: return Result.failure(err("URL fehlt"))
        val dest = inputData.getString(KEY_DEST) ?: return Result.failure(err("Ziel fehlt"))
        val type = inputData.getString(KEY_TYPE) ?: TYPE_DIRECT
        val notifId = if (type == TYPE_EXTRACT) NOTIF_ID_TTS else NOTIF_ID_LLM

        // Foreground service NUR einmal starten
        setForeground(buildForegroundInfo("Vorbereiten …", -1f, notifId))

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        var lastError: String? = null

        val flow = if (type == TYPE_EXTRACT)
            ModelDownloader.downloadAndExtract(url, File(dest))
        else
            ModelDownloader.download(url, File(dest))

        flow.collect { p ->
            lastError = p.error

            val label = when {
                p.error != null              -> "Fehler"
                p.phase == "extract"
                        && !p.done           -> "Entpacken …"
                p.done                       -> "Fertig"
                p.total > 0                  ->
                    "${(p.bytes * 100 / p.total).toInt()} %  " +
                    "${fmtMB(p.bytes)} / ${fmtMB(p.total)}"
                else                         -> "${fmtMB(p.bytes)} geladen …"
            }
            val pct = if (p.total > 0) p.bytes.toFloat() / p.total else -1f

            // Notification direkt updaten – kein setForeground() in der Schleife
            nm.notify(notifId, buildNotification(label, pct))
            setProgress(workDataOf(KEY_BYTES to p.bytes, KEY_TOTAL to p.total, KEY_PHASE to p.phase))
        }

        return if (lastError != null) Result.failure(err(lastError!!)) else Result.success()
    }

    private fun buildNotification(text: String, progress: Float): Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Sprach-Assistent – Download")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (progress >= 0f) builder.setProgress(100, (progress * 100).toInt(), false)
        else                builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun buildForegroundInfo(text: String, progress: Float, notifId: Int): ForegroundInfo {
        val notif = buildNotification(text, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(notifId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(notifId, notif)
    }

    private fun err(msg: String) = workDataOf(KEY_ERROR to msg)

    companion object {
        const val KEY_URL   = "url"
        const val KEY_DEST  = "dest"
        const val KEY_TYPE  = "type"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val TYPE_DIRECT  = "direct"
        const val TYPE_EXTRACT = "extract"
        const val WORK_TTS = "download_tts"
        const val WORK_LLM = "download_llm"

        private const val CHANNEL_ID  = "voicellm_downloads"
        private const val NOTIF_ID_TTS = 1001
        private const val NOTIF_ID_LLM = 1002

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Modell-Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Fortschritt beim Herunterladen von KI-Modellen" }
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }
    }
}

private fun fmtMB(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}
