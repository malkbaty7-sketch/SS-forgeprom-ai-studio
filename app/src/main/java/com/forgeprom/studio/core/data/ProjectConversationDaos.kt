package com.forgeprom.studio.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectConversationDao {
    @Query("SELECT * FROM project_conversations WHERE projectId = :projectId ORDER BY updatedAt DESC, id DESC")
    fun forProject(projectId: Long): Flow<List<ProjectConversationEntity>>

    @Query("SELECT * FROM project_conversations WHERE id = :id")
    suspend fun byId(id: Long): ProjectConversationEntity?

    @Query("SELECT * FROM project_conversations ORDER BY projectId, createdAt, id")
    suspend fun allOnce(): List<ProjectConversationEntity>

    @Insert
    suspend fun insert(conversation: ProjectConversationEntity): Long

    @Update
    suspend fun update(conversation: ProjectConversationEntity)

    @Query("DELETE FROM project_conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProjectMessageDao {
    @Query("SELECT * FROM project_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun forConversation(conversationId: Long): Flow<List<ProjectMessageEntity>>

    @Query("SELECT * FROM project_messages WHERE conversationId = :conversationId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentForConversation(conversationId: Long, limit: Int): List<ProjectMessageEntity>

    @Query("SELECT m.conversationId, c.title AS conversationTitle, m.role, m.content, m.createdAt " +
        "FROM project_messages AS m INNER JOIN project_conversations AS c ON c.id = m.conversationId " +
        "WHERE c.projectId = :projectId AND m.conversationId != :excludeConversationId " +
        "ORDER BY m.createdAt DESC, m.id DESC LIMIT :limit")
    suspend fun contextFromOtherConversations(
        projectId: Long,
        excludeConversationId: Long,
        limit: Int
    ): List<ProjectMessageContext>

    @Insert
    suspend fun insert(message: ProjectMessageEntity): Long

    @Query("SELECT * FROM project_messages ORDER BY conversationId, createdAt, id")
    suspend fun allOnce(): List<ProjectMessageEntity>
}
