package com.forgeprom.studio.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * قاعدة البيانات المحلية — Room، الإصدار 2.
 * v2 يضيف سجل إصدارات الموجّهات (لقطات نصية عند الحفظ).
 */
@Database(
    entities = [
        ProjectEntity::class,
        PromptEntity::class,
        AnalysisEntity::class,
        ActivityEntity::class,
        EditorFileEntity::class,
        ImageAnalysisEntity::class,
        PromptVersionEntity::class
    ],
    version = 2,
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

        fun build(context: Context): ForgepromDatabase =
            Room.databaseBuilder(context, ForgepromDatabase::class.java, "forgeprom.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
