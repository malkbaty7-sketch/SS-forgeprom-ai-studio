package com.forgeprom.studio.core.jobs

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * مخزن مهام المشاهد المعلّقة والنتائج المكتملة في الخلفية.
 *
 * المهمة تُسجَّل لحظة إرسالها إلى الطابور، ويجدول عامل WorkManager متابعتها.
 * عند الاكتمال تُحفظ النتيجة محلياً حتى يفتح المستخدم المحرر ويقرر الإدراج.
 */
class PendingSceneStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enqueue(jobId: String, promptId: Long, title: String) {
        if (promptId <= 0) return
        prefs.edit().putString(
            jobKey(jobId),
            SceneJobCodec.encodeJob(
                QueuedSceneJob(
                    jobId = jobId,
                    promptId = promptId,
                    title = title.take(SceneJobCodec.MAX_TITLE_CHARS),
                    savedAt = System.currentTimeMillis()
                )
            )
        ).apply()
        val request = OneTimeWorkRequestBuilder<SceneJobWorker>()
            .setInputData(workDataOf(SceneJobWorker.KEY_JOB to jobId, SceneJobWorker.KEY_PROMPT to promptId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniqueWork(uniqueWork(jobId), ExistingWorkPolicy.KEEP, request)
        }
    }

    fun job(jobId: String): QueuedSceneJob? = SceneJobCodec.decodeJob(prefs.getString(jobKey(jobId), null))

    /** المهام النشطة لهذا الموجّه، الأحدث أولاً. */
    fun activeJobs(promptId: Long): List<QueuedSceneJob> = prefs.all.entries
        .asSequence()
        .filter { it.key.startsWith(JOB_PREFIX) }
        .mapNotNull { SceneJobCodec.decodeJob(it.value as? String) }
        .filter { it.promptId == promptId }
        .sortedByDescending { it.savedAt }
        .toList()

    /** Remove the persisted record without cancelling work (used by the worker itself). */
    fun removeJobRecord(jobId: String) {
        prefs.edit().remove(jobKey(jobId)).apply()
    }

    fun clearJob(jobId: String) {
        removeJobRecord(jobId)
        runCatching { WorkManager.getInstance(appContext).cancelUniqueWork(uniqueWork(jobId)) }
    }

    fun saveResult(result: CompletedScene) {
        prefs.edit().putString(resultKey(result.promptId), SceneJobCodec.encodeResult(result)).apply()
    }

    fun resultFor(promptId: Long): CompletedScene? =
        SceneJobCodec.decodeResult(prefs.getString(resultKey(promptId), null))

    fun clearResult(promptId: Long) {
        prefs.edit().remove(resultKey(promptId)).apply()
    }

    private fun jobKey(jobId: String) = JOB_PREFIX + jobId
    private fun resultKey(promptId: Long) = RESULT_PREFIX + promptId
    private fun uniqueWork(jobId: String) = "scene-job-$jobId"

    private companion object {
        const val PREFS = "pending_scene_jobs"
        const val JOB_PREFIX = "job_"
        const val RESULT_PREFIX = "result_"
    }
}
