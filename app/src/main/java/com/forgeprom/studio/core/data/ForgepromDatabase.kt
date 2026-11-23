package com.forgeprom.studio.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * قاعدة البيانات المحلية — Room، الإصدار 3.
 * v2 أضاف سجل الإصدارات؛ v3 يضيف محادثات المشروع ورسائلها.
 */
@Database(
    entities = [
        ProjectEntity::class,
        PromptEntity::class,
        AnalysisEntity::class,
        ActivityEntity::class,
        EditorFileEntity::class,
        ImageAnalysisEntity::class,
        PromptVersionEntity::class,
        ProjectConversationEntity::class,
        ProjectMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ForgepromDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun promptDao(): PromptDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun activityDao(): ActivityDao
    abstract fun editorFileDao(): EditorFileDao
    abstract fun imageAnalysisDao(): ImageAnalysisDao
    abstract fun promptVersionDao(): PromptVersionDao
    abstract fun projectConversationDao(): ProjectConversationDao
    abstract fun projectMessageDao(): ProjectMessageDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prompt_versions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`promptId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `project_conversations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_conversations_projectId` ON `project_conversations` (`projectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `project_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL, `role` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`conversationId`) REFERENCES `project_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_messages_conversationId_createdAt_id` ON `project_messages` (`conversationId`, `createdAt`, `id`)")
            }
        }

        fun build(context: Context): ForgepromDatabase =
            Room.databaseBuilder(context, ForgepromDatabase::class.java, "forgeprom.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
