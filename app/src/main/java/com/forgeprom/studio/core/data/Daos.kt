package com.forgeprom.studio.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun activeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun archivedProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun byId(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun search(q: String): Flow<List<ProjectEntity>>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET name = :name, description = :description, tags = :tags, updatedAt = :now WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, description: String, tags: String, now: Long): Int

    @Query("UPDATE projects SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("UPDATE projects SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    /** نسخة واحدة لكل المشاريع — للنسخ الاحتياطي وكشف التكرار عند الاستعادة. */
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun allOnce(): List<ProjectEntity>
}

/** سجل الموجهات المحفوظة مع اسم المشروع لعرضها في مكتبة واحدة. */
data class PromptLibraryItem(
    val id: Long,
    val projectId: Long,
    val title: String,
    val content: String,
    val qualityScore: Int,
    val goal: String,
    val tech: String,
    val updatedAt: Long,
    @ColumnInfo(name = "projectName") val projectName: String
)

@Dao
interface PromptDao {
    @Query("SELECT prompts.id, prompts.projectId, prompts.title, prompts.content, prompts.qualityScore, prompts.goal, prompts.tech, prompts.updatedAt, projects.name AS projectName " +
        "FROM prompts INNER JOIN projects ON projects.id = prompts.projectId " +
        "ORDER BY prompts.updatedAt DESC, prompts.id DESC")
    fun library(): Flow<List<PromptLibraryItem>>
    @Query("SELECT * FROM prompts WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun forProject(projectId: Long): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun byId(id: Long): PromptEntity?

    @Query("SELECT * FROM prompts WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 4")
    suspend fun allForProject(projectId: Long): List<PromptEntity>

    @Insert
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int

    @Query("SELECT * FROM prompts ORDER BY projectId, updatedAt DESC")
    suspend fun allOnce(): List<PromptEntity>
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun forProject(projectId: Long): Flow<List<AnalysisEntity>>

    @Insert
    suspend fun insert(analysis: AnalysisEntity): Long

    @Query("SELECT * FROM analyses WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 3")
    suspend fun allForProject(projectId: Long): List<AnalysisEntity>

    @Query("SELECT * FROM analyses ORDER BY projectId, createdAt DESC")
    suspend fun allOnce(): List<AnalysisEntity>
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY at DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<ActivityEntity>>

    @Insert
    suspend fun insert(activity: ActivityEntity)

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int
}

@Dao
interface EditorFileDao {
    @Query("SELECT * FROM editor_files WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun forProject(projectId: Long): Flow<List<EditorFileEntity>>

    @Query("SELECT * FROM editor_files WHERE id = :id")
    suspend fun byId(id: Long): EditorFileEntity?

    @Query("SELECT * FROM editor_files WHERE projectId = :projectId ORDER BY updatedAt DESC LIMIT 3")
    suspend fun allForProject(projectId: Long): List<EditorFileEntity>

    @Insert
    suspend fun insert(file: EditorFileEntity): Long

    @Update
    suspend fun update(file: EditorFileEntity)

    @Query("DELETE FROM editor_files WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM editor_files ORDER BY projectId, updatedAt DESC")
    suspend fun allOnce(): List<EditorFileEntity>
}

@Dao
interface ImageAnalysisDao {
    @Query("SELECT * FROM image_analyses WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun forProject(projectId: Long): Flow<List<ImageAnalysisEntity>>

    @Insert
    suspend fun insert(analysis: ImageAnalysisEntity): Long

    @Query("SELECT * FROM image_analyses WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 3")
    suspend fun allForProject(projectId: Long): List<ImageAnalysisEntity>

    @Query("SELECT * FROM image_analyses ORDER BY projectId, createdAt DESC")
    suspend fun allOnce(): List<ImageAnalysisEntity>
}

@Dao
interface PromptVersionDao {
    @Query("SELECT * FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC, id DESC LIMIT 20")
    fun recent(promptId: Long): Flow<List<PromptVersionEntity>>

    @Query("SELECT * FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(promptId: Long): PromptVersionEntity?

    @Insert
    suspend fun insert(version: PromptVersionEntity): Long

    /** يُبقي أحدث N نسخة فقط لكل موجّه. */
    @Query("DELETE FROM prompt_versions WHERE promptId = :promptId AND id NOT IN (SELECT id FROM prompt_versions WHERE promptId = :promptId ORDER BY createdAt DESC, id DESC LIMIT :keep)")
    suspend fun trim(promptId: Long, keep: Int)

    @Query("DELETE FROM prompt_versions WHERE promptId = :promptId")
    suspend fun clear(promptId: Long)
}
