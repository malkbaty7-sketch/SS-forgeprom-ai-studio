package com.forgeprom.studio.core.ai

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import android.system.Os
import com.forgeprom.studio.core.settings.AppSettings
import com.forgeprom.studio.core.settings.PromptLanguage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Inference and the imported model stay on the device. No network fallback in this engine. */
class LocalNeuralEngine(context: Context) : AiEngine {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "neural-model")
    private val model = File(directory, "model.task")
    private val lock = Mutex()
    override val id = "local.neural"
    override val label = "نموذج عصبي محلي"
    override val isLocal = true

    val supportedDevice: Boolean
        get() = Process.is64Bit() && "arm64-v8a" in Build.SUPPORTED_ABIS

    override suspend fun status(settings: AppSettings): EngineStatus = withContext(Dispatchers.IO) {
        val present = supportedDevice && model.isFile
        EngineStatus(id, label, present, when {
            !supportedDevice -> "يتطلب جهاز Android بمعمارية ARM64."
            !present -> "استورد حزمة MediaPipe LLM متوافقة (.task)."
            else -> "الحزمة مثبّتة — الاستدلال على الجهاز دون اتصال."
        })
    }

    private fun open(file: File): LlmInference = LlmInference.createFromOptions(
        app, LlmInference.LlmInferenceOptions.builder().setModelPath(file.absolutePath)
            .setMaxTokens(CONTEXT_TOKENS).build()
    )

    suspend fun install(uri: Uri) = withContext(Dispatchers.IO) {
        lock.withLock {
            require(supportedDevice)
            check(directory.isDirectory || directory.mkdirs())
            val pending = File(directory, "pending.task")
            try {
                var bytes = 0L
                requireNotNull(app.contentResolver.openInputStream(uri)).use { input ->
                    pending.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            bytes += n
                            require(bytes <= MAX_MODEL_BYTES)
                            output.write(buffer, 0, n)
                        }
                    }
                }
                require(bytes > 0)
                open(pending).use { /* Validate the bundle before replacing the current model. */ }
                ensureActive()
                Os.rename(pending.absolutePath, model.absolutePath)
            } finally {
                pending.delete()
            }
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        lock.withLock { check(!model.exists() || model.delete()) }
    }

    override suspend fun generate(request: GenerationRequest): Result<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                check(supportedDevice && model.isFile) { "ثبّت نموذجًا عصبيًا متوافقًا من شاشة المحركات." }
                require(!request.useGatewayQueue) { "طابور البوابة غير متاح للنموذج المحلي." }
                val prompt = localNeuralPrompt(request)
                val text = open(model).use { llm ->
                    require(llm.sizeInTokens(prompt) <= CONTEXT_TOKENS - 512) {
                        "المدخل طويل جدًا للنموذج المحلي؛ اختصر الوصف أو السياق."
                    }
                    llm.generateResponse(prompt).trim()
                }
                ensureActive()
                check(text.isNotEmpty()) { "لم يُنتج النموذج المحلي نصًا." }
                Result.success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } catch (e: LinkageError) {
                Result.failure(IllegalStateException("تعذّر تشغيل مكتبة النموذج على هذا الجهاز.", e))
            }
        }
    }

    override suspend fun analyzeImage(bytes: ByteArray, mime: String, note: String): Result<ImageAnalysisResult> =
        Result.failure(UnsupportedOperationException("النموذج المحلي يدعم توليد النص فقط."))

    private companion object {
        const val MAX_MODEL_BYTES = 4L * 1024 * 1024 * 1024
        const val CONTEXT_TOKENS = 2048
    }
}

internal fun localNeuralPrompt(request: GenerationRequest): String = buildString {
    appendLine(request.cinematicSystem ?: "Create a structured, actionable prompt for a coding assistant. Return only the prompt.")
    if (request.cinematicSystem == null) appendLine(when (request.promptLanguage) {
        PromptLanguage.AR -> "Write the result in Arabic."
        PromptLanguage.EN -> "Write the result in English."
        PromptLanguage.DUAL -> "Write the result in Arabic and English."
    })
    appendLine("Request: ${request.instruction}")
    if (request.goal.isNotBlank()) appendLine("Goal: ${request.goal}")
    if (request.tech.isNotBlank()) appendLine("Technology: ${request.tech}")
    if (request.projectContext.isNotBlank()) appendLine("Context: ${request.projectContext}")
}
