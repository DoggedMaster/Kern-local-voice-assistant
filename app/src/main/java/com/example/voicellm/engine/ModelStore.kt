package com.example.voicellm.engine

import android.content.Context
import java.io.File

/**
 * Modelle sind zu gross fuer assets/ (Gemma 4 E2B int4 ~ 2 GB, Kokoro ~ 300 MB).
 * Wir legen sie in <internal>/files/models/ ab. Der Nutzer laedt die Dateien
 * entweder beim ersten Start aus dem Netz herunter, per USB-Push
 *   adb push model.task /sdcard/Android/data/com.example.voicellm/files/models/
 * oder aus einem Share-Intent.
 */
object ModelStore {
    fun modelsDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelPath(context: Context, name: String): String =
        File(modelsDir(context), name).absolutePath

    fun exists(context: Context, name: String): Boolean =
        File(modelsDir(context), name).exists()
}
