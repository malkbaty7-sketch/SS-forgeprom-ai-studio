package com.forgeprom.studio.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** محادثة مستقلة داخل مشروع؛ معلومات المشروع مشتركة بين جميع محادثاته. */
@Entity(
    tableName = "project_conversations",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["projectId"])]
)
data class ProjectConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String = "محادثة جديدة",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** رسائل المستخدم وردود المساعد محفوظة محلياً وبترتيب زمني ثابت. */
@Entity(
    tableName = "project_messages",
    foreignKeys = [ForeignKey(
        entity = ProjectConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["conversationId", "createdAt", "id"])]
)
data class ProjectMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Projection خفيفة لعرض رسائل محادثات المشروع الأخرى في سياق المشروع المشترك. */
data class ProjectMessageContext(
    val conversationId: Long,
    @ColumnInfo(name = "conversationTitle") val conversationTitle: String,
    val role: String,
    val content: String,
    val createdAt: Long
)
