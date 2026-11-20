package com.forgeprom.studio.core.ai

import com.forgeprom.studio.core.security.ApiKeyStore
import com.forgeprom.studio.core.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import android.os.SystemClock
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * R5 — محرك متوافق مع OpenAI (متعدد المزودين: OpenAI، Groq، Together، إلخ)
 * عبر baseUrl وmodel قابلين للتهيئة. يستخدم فقط بعد بوابة الموافقة.
 */
class OpenAiCompatEngine(private val apiKeyStore: ApiKeyStore) : AiEngine {
    override val id = "cloud.openai_compat"
    override val label = "متوافق OpenAI (سحابي اختياري)"
    override val isLocal = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jobsClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun status(settings: AppSettings): EngineStatus {
        val hasKey = apiKeyStore.openAiKey().isNotBlank()
        return EngineStatus(
            id = id,
            label = label,
            isReady = hasKey,
            statusLabel = when {
                !hasKey -> "غير مهيأ — أضف المفتاح والعنوان في الإعدادات"
                !settings.cloudConsent -> "مفتاح موجود لكن موافقة السحابة غير ممنوحة"
                else -> "جاهز — " + apiKeyStore.openAiModel() + " @ " + apiKeyStore.openAiBaseUrl()
            }
        )
    }

    override suspend fun generate(request: GenerationRequest): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val key = apiKeyStore.openAiKey()
                require(key.isNotBlank()) { "مفتاح API غير مضبوط" }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put(
                        "content",
                        request.cinematicSystem ?: "You generate professional coding PROMPTS (not code) for AI code platforms. " +
                            "Always include: role, objective, context, request type, tech stack, UI spec, states, interactions, accessibility, anti-bloat code quality constraints, acceptance criteria, output format. State assumptions explicitly."
                    ))
                    .put(JSONObject().put("role", "user").put(
                        "content",
                        if (request.cinematicSystem != null) request.instruction
                        else "Goal: " + request.goal + "\nTech: " + request.tech +
                            (if (request.projectContext.isNotBlank()) "\nProject context: " + request.projectContext else "") +
                            "\nRequest: " + request.instruction
                    ))
                val body = JSONObject()
                    .put("model", apiKeyStore.openAiModel())
                    .put("messages", messages)
                if (request.useGatewayQueue) return@runCatching queuedResult(key, body)
                val url = apiKeyStore.openAiBaseUrl().trimEnd('/') + "/chat/completions"
                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + key)
                    .post(body.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(httpRequest).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    require(resp.isSuccessful) { "فشل الطلب (HTTP " + resp.code + ")" }
                    JSONObject(text)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        }

    private suspend fun queuedResult(key: String, payload: JSONObject): String {
        val base = apiKeyStore.openAiBaseUrl().trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null) { "يلزم عنوان HTTPS للبوابة" }
        val jobs = base.newBuilder().addPathSegment("jobs").build()
        val submit = Request.Builder().url(jobs).header("Authorization", "Bearer $key")
            .post(payload.toString().toRequestBody(jsonMedia)).build()
        val id = jobsClient.newCall(submit).execute().use { response ->
            require(response.code == 202) { "تعذر إرسال المهمة (HTTP ${response.code})" }
            JSONObject(response.body?.string().orEmpty()).getString("id")
        }
        require(id.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")))
        val status = Request.Builder().url(jobs.newBuilder().addPathSegment(id).build())
            .header("Authorization", "Bearer $key").build()
        val deadline = SystemClock.elapsedRealtime() + 120_000
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(2000)
            val data = jobsClient.newCall(status).execute().use { response ->
                require(response.isSuccessful) { "تعذر الاستعلام عن المهمة (HTTP ${response.code})" }
                JSONObject(response.body?.string().orEmpty())
            }
            when (data.getString("status")) {
                "done" -> return data.getJSONObject("result").getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                "queued", "running" -> Unit
                else -> error("لم تنجح مهمة التوليد")
            }
        }
        error("انتهت مهلة متابعة المهمة؛ قد يستمر التنفيذ على الخادم")
    }

    internal suspend fun webSearch(query: String): JSONArray = withContext(Dispatchers.IO) {
        val base = apiKeyStore.openAiBaseUrl().trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null)
        val key = apiKeyStore.openAiKey()
        require(key.isNotBlank())
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegment("web").addPathSegment("search").build())
            .header("Authorization", "Bearer $key")
            .post(JSONObject().put("query", query).toString().toRequestBody(jsonMedia)).build()
        jobsClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                when (response.code) {
                    401, 403 -> "تحقّق من رمز حساب البوابة."
                    404 -> "البحث غير متاح على عنوان البوابة المهيّأ."
                    429 -> "بلغت حد الطلبات. انتظر ثم أعد المحاولة."
                    else -> "تعذّر البحث الآن. حاول مرة أخرى."
                }
            }
            JSONObject(response.body?.string().orEmpty()).getJSONArray("results")
        }
    }

    internal suspend fun billingInvoices(): JSONArray = withContext(Dispatchers.IO) {
        val base = apiKeyStore.openAiBaseUrl().trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null)
        val key = apiKeyStore.openAiKey()
        require(key.isNotBlank())
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegment("billing").addPathSegment("invoices").build())
            .header("Authorization", "Bearer $key").build()
        jobsClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            require(response.isSuccessful)
            JSONObject(response.body?.string().orEmpty()).getJSONArray("invoices")
        }
    }

    suspend fun storyIndex(book: String, action: String, body: JSONObject? = null): JSONObject =
        storyIndexSession(book)(action, body)

    // Capture the destination and account once so a resumed replacement cannot switch indexes.
    internal fun storyIndexSession(book: String): suspend (String, JSONObject?) -> JSONObject {
        val base = apiKeyStore.openAiBaseUrl().trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() &&
            base.query == null && base.fragment == null)
        val key = apiKeyStore.openAiKey()
        require(key.isNotBlank())
        return { action, body ->
            withContext(Dispatchers.IO) {
                val path = base.newBuilder().addPathSegment("story").addPathSegment(book)
                val request = Request.Builder().header("Authorization", "Bearer $key")
                when (action) {
                    "chapters", "search" -> request.url(path.addPathSegment(action).build())
                        .post(requireNotNull(body).toString().toRequestBody(jsonMedia))
                    "delete" -> request.url(path.build()).delete()
                    else -> error("Unknown story action")
                }
                jobsClient.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
                    .newCall(request.build()).execute().use { response ->
                        require(response.isSuccessful) { "HTTP ${response.code}" }
                        JSONObject(response.body?.string().orEmpty())
                    }
            }
        }
    }

    override suspend fun analyzeImage(bytes: ByteArray, mime: String, note: String): Result<ImageAnalysisResult> =
        Result.failure(UnsupportedOperationException(
            "تحليل الصور عبر المحرك المتوافق OpenAI غير مدعوم في هذه النسخة — استخدم Gemini."
        ))
}
