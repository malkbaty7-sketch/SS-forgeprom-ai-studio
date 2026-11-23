package com.forgeprom.studio.core.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forgeprom.studio.core.security.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * يتابع مهمة مشهد أُرسلت إلى طابور البوابة أثناء إغلاق التطبيق.
 *
 * لا يعيد إرسال التوليد أبداً (قد يكون مدفوعاً) — يستعلم عن الحالة فقط.
 * عند الاكتمال يحفظ النتيجة محلياً ويُشعر المستخدم إن سمح النظام.
 */
class SceneJobWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB) ?: return@withContext Result.failure()
        val store = PendingSceneStore(applicationContext)
        val job = store.job(jobId) ?: return@withContext Result.success()
        val promptId = inputData.getLong(KEY_PROMPT, 0L)
        if (promptId <= 0 || promptId != job.promptId) {
            return@withContext failTerminal(store, jobId, job.promptId)
        }

        val keys = ApiKeyStore(applicationContext)
        val base = runCatching { keys.openAiBaseUrl().trimEnd('/').toHttpUrl() }.getOrNull()
            ?: return@withContext failTerminal(store, jobId, promptId)
        if (!base.isHttps || base.username.isNotEmpty() || base.password.isNotEmpty() ||
            base.query != null || base.fragment != null
        ) {
            return@withContext failTerminal(store, jobId, promptId)
        }
        val key = keys.openAiKey()
        if (key.isBlank()) return@withContext failTerminal(store, jobId, promptId)

        try {
            val url = base.newBuilder().addPathSegment("jobs").addPathSegment(jobId).build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext if (response.code in setOf(401, 403, 404)) {
                        failTerminal(store, jobId, promptId)
                    } else {
                        retryOrFail(store, jobId, promptId)
                    }
                }
                val data = JSONObject(response.body?.string().orEmpty())
                when (data.optString("status")) {
                    "done" -> {
                        val content = extractContent(data)
                        if (content.isBlank()) {
                            failTerminal(store, jobId, promptId)
                        } else {
                            store.saveResult(
                                CompletedScene(
                                    promptId = promptId,
                                    title = job.title,
                                    content = content,
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                            store.removeJobRecord(jobId)
                            SceneNotifier.notifyCompleted(applicationContext, promptId, job.title)
                            Result.success()
                        }
                    }
                    "queued", "running" -> retryOrFail(store, jobId, promptId)
                    else -> failTerminal(store, jobId, promptId)
                }
            }
        } catch (_: Exception) {
            retryOrFail(store, jobId, promptId)
        }
    }

    private fun retryOrFail(store: PendingSceneStore, jobId: String, promptId: Long): Result =
        if (runAttemptCount >= MAX_ATTEMPTS) {
            failTerminal(store, jobId, promptId)
        } else {
            Result.retry()
        }

    private fun failTerminal(store: PendingSceneStore, jobId: String, promptId: Long): Result {
        store.removeJobRecord(jobId)
        SceneNotifier.notifyFailed(applicationContext, promptId)
        return Result.failure()
    }

    private fun extractContent(data: JSONObject): String =
        data.optJSONObject("result")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()

    companion object {
        const val KEY_JOB = "job_id"
        const val KEY_PROMPT = "prompt_id"
        const val MAX_ATTEMPTS = 20
    }
}
