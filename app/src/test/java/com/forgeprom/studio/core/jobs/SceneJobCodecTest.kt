package com.forgeprom.studio.core.jobs

import com.forgeprom.studio.core.backup.SimpleJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneJobCodecTest {

    private val jobId = "7da74cd1-ead8-4a35-85b9-72c1b749d6b3"

    @Test
    fun `job round trip keeps identity and prompt`() {
        val job = QueuedSceneJob(jobId, 42L, "مشهد المطر", 1_700_000_000_000L)
        assertEquals(job, SceneJobCodec.decodeJob(SceneJobCodec.encodeJob(job)))
    }

    @Test
    fun `job rejects malformed ids and prompts`() {
        assertNull(SceneJobCodec.decodeJob(null))
        assertNull(SceneJobCodec.decodeJob("{}"))
        assertNull(SceneJobCodec.decodeJob("""{"jobId":"not-a-uuid","promptId":5}"""))
        assertNull(SceneJobCodec.decodeJob("""{"jobId":"$jobId","promptId":0}"""))
        assertNull(SceneJobCodec.decodeJob("""{"jobId":123,"promptId":5}"""))
    }

    @Test
    fun `result round trip keeps arabic content`() {
        val result = CompletedScene(
            promptId = 7L,
            title = "المشهد",
            content = "مشهد ليلي\nيمطر بغزارة «حوار»",
            completedAt = 1_700_000_100_000L
        )
        assertEquals(result, SceneJobCodec.decodeResult(SceneJobCodec.encodeResult(result)))
    }

    @Test
    fun `result rejects blank oversized and invalid content`() {
        assertNull(SceneJobCodec.decodeResult(null))
        assertNull(SceneJobCodec.decodeResult("{}"))
        assertNull(SceneJobCodec.decodeResult("""{"promptId":7,"content":"   "}"""))
        val huge = SimpleJson.stringify(
            mapOf("promptId" to 7, "content" to "ا".repeat(SceneJobCodec.MAX_CONTENT_CHARS + 1))
        )
        assertNull(SceneJobCodec.decodeResult(huge))
    }

    @Test
    fun `encoded title is capped`() {
        val result = CompletedScene(1L, "ع".repeat(1_000), "نص", 0L)
        val decoded = SceneJobCodec.decodeResult(SceneJobCodec.encodeResult(result))
        assertEquals(SceneJobCodec.MAX_TITLE_CHARS, decoded?.title?.length)
    }
}
