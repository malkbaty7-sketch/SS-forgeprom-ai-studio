package com.forgeprom.studio.core.jobs

import com.forgeprom.studio.core.backup.SimpleJson

/** مهمة مشهد أُرسلت إلى طابور البوابة وتتابَع في الخلفية. */
data class QueuedSceneJob(
    val jobId: String,
    val promptId: Long,
    val title: String,
    val savedAt: Long
)

/** مشهد اكتمل في الخلفية وينتظر إدراجه في المحرر. */
data class CompletedScene(
    val promptId: Long,
    val title: String,
    val content: String,
    val completedAt: Long
)

/**
 * ترميز حالة مهام المشهد في SharedPreferences.
 * كائن نقي بلا Android ليسهل اختباره، ويرفض أي مدخلات مشوّهة أو ضخمة.
 */
object SceneJobCodec {

    const val MAX_TITLE_CHARS = 200
    const val MAX_CONTENT_CHARS = 100_000
    private val UUID = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")

    fun encodeJob(job: QueuedSceneJob): String = SimpleJson.stringify(
        linkedMapOf<String, Any?>(
            "jobId" to job.jobId,
            "promptId" to job.promptId,
            "title" to job.title.take(MAX_TITLE_CHARS),
            "savedAt" to job.savedAt
        )
    )

    fun decodeJob(raw: String?): QueuedSceneJob? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = SimpleJson.parse(raw) as? Map<*, *> ?: return null
            val jobId = root["jobId"] as? String ?: return null
            if (!UUID.matches(jobId)) return null
            val promptId = (root["promptId"] as? Number)?.toLong() ?: return null
            if (promptId <= 0) return null
            QueuedSceneJob(
                jobId = jobId,
                promptId = promptId,
                title = ((root["title"] as? String) ?: "").take(MAX_TITLE_CHARS),
                savedAt = (root["savedAt"] as? Number)?.toLong() ?: 0L
            )
        }.getOrNull()
    }

    fun encodeResult(result: CompletedScene): String = SimpleJson.stringify(
        linkedMapOf<String, Any?>(
            "promptId" to result.promptId,
            "title" to result.title.take(MAX_TITLE_CHARS),
            "content" to result.content.take(MAX_CONTENT_CHARS),
            "completedAt" to result.completedAt
        )
    )

    fun decodeResult(raw: String?): CompletedScene? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = SimpleJson.parse(raw) as? Map<*, *> ?: return null
            val promptId = (root["promptId"] as? Number)?.toLong() ?: return null
            if (promptId <= 0) return null
            val content = (root["content"] as? String)?.takeIf { it.isNotBlank() } ?: return null
            if (content.length > MAX_CONTENT_CHARS) return null
            CompletedScene(
                promptId = promptId,
                title = ((root["title"] as? String) ?: "").take(MAX_TITLE_CHARS),
                content = content,
                completedAt = (root["completedAt"] as? Number)?.toLong() ?: 0L
            )
        }.getOrNull()
    }
}
