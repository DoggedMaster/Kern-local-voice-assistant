# Kern – Lokaler Sprachassistent für Android
# Kern - Local LLM Voice Assistant for Android

[Deutsch](#deutsch) · [English](#english)

---

## Deutsch

Vollständig on-device: Mikrofon → **Android STT** → **Gemma 4 (LiteRT)** → **Piper TTS** → Lautsprecher.  
Keine Cloud, kein API-Key, läuft vollständig offline.

### Features

- **Spracherkennung** via Android SpeechRecognizer (kein Download nötig)
- **LLM-Inferenz** mit [LiteRT LLM SDK](https://ai.google.dev/edge/litert/inference/llm) – Gemma 4 E2B / E4B, direkt auf dem Gerät
- **Text-to-Speech** mit [Piper VITS](https://github.com/rhasspy/piper) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) – mehrere Stimmen auf Deutsch und Englisch
- **Sprachen**: Deutsch, Englisch, Multilingual (Assistent antwortet in der Sprache des Nutzers)
- **Anpassbarer System-Prompt** für eigene Assistenten-Persönlichkeit
- **Einrichtungsassistent** beim ersten Start
- **Modelle werden in der App heruntergeladen** – kein manuelles Kopieren nötig

### Anforderungen

- Android 10+ (API 29)
- arm64 Gerät
- ≥ 6 GB RAM empfohlen (Gemma 4 E2B), ≥ 8 GB für E4B
- ca. 3–4 GB freier Speicher für Modelle

### Build

**Voraussetzungen:** Android Studio, Java 17, Android SDK 35

#### 1. sherpa-onnx AAR herunterladen

```bash
mkdir -p app/libs
curl -L -o app/libs/sherpa-onnx-1.12.39.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.39/sherpa-onnx-v1.12.39-android.aar
```

#### 2. Bauen

```bash
JAVA_HOME=/usr/lib/jvm/java-17-... ./gradlew assembleRelease
```

#### 3. Modelle

Beim ersten Start werden LLM-Modell und TTS-Stimme automatisch zum Download angeboten.  
Alles wird unter `<internal>/files/models/` gespeichert.

### Lizenz

Apache 2.0 — siehe [LICENSE](LICENSE)

---

## English

Fully on-device: Microphone → **Android STT** → **Gemma 4 (LiteRT)** → **Piper TTS** → Speaker.  
No cloud, no API key, runs completely offline.

### Features

- **Speech recognition** via Android SpeechRecognizer (no download required)
- **LLM inference** with [LiteRT LLM SDK](https://ai.google.dev/edge/litert/inference/llm) – Gemma 4 E2B / E4B, fully on-device
- **Text-to-speech** with [Piper VITS](https://github.com/rhasspy/piper) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) – multiple German and English voices
- **Languages**: German, English, Multilingual (assistant replies in the user's language)
- **Customizable system prompt** for your own assistant personality
- **Setup wizard** on first launch
- **Models are downloaded inside the app** – no manual file copying needed

### Requirements

- Android 10+ (API 29)
- arm64 device
- ≥ 6 GB RAM recommended (Gemma 4 E2B), ≥ 8 GB for E4B
- ~3–4 GB free storage for models

### Build

**Prerequisites:** Android Studio, Java 17, Android SDK 35

#### 1. Download sherpa-onnx AAR

```bash
mkdir -p app/libs
curl -L -o app/libs/sherpa-onnx-1.12.39.aar \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.12.39/sherpa-onnx-v1.12.39-android.aar
```

#### 2. Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-... ./gradlew assembleRelease
```

#### 3. Models

On first launch the app offers to download the LLM model and a TTS voice automatically.  
Everything is stored under `<internal>/files/models/`.

### License

Apache 2.0 — see [LICENSE](LICENSE)
