package com.example.voicellm.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Chunked Token-Stream -> ganze Saetze. Wichtig fuer Streaming-TTS:
 * Der erste Satz kann synthetisiert und abgespielt werden, waehrend das LLM
 * den naechsten noch generiert.
 */
fun Flow<String>.asSentences(): Flow<String> = flow {
    val enders = ".?!\n"
    val buf = StringBuilder()
    collect { token ->
        buf.append(token)
        // Moegliche Saetze rauspoppen, solange mehr als ein Endzeichen drin ist.
        while (true) {
            var idx = -1
            for (i in buf.indices) {
                if (buf[i] in enders && i >= 8) { idx = i; break }
            }
            if (idx == -1) break
            val sentence = buf.substring(0, idx + 1).trim()
            buf.delete(0, idx + 1)
            if (sentence.isNotEmpty()) emit(sentence)
        }
    }
    val tail = buf.toString().trim()
    if (tail.isNotEmpty()) emit(tail)
}
