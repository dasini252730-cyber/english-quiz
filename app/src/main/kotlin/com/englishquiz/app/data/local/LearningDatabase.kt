package com.englishquiz.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedExpressionEntity::class, LearningSessionEntity::class, DailyContentEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        fun create(
            context: Context,
            databaseName: String = "english-quiz.db",
        ): LearningDatabase {
            val applicationContext = context.applicationContext ?: context
            val databaseFile = applicationContext.getDatabasePath(databaseName)
            val databaseDirectory = checkNotNull(databaseFile.parentFile)
            if (!databaseDirectory.isDirectory &&
                !databaseDirectory.mkdirs() &&
                !databaseDirectory.isDirectory
            ) {
                error("Could not create the app database directory.")
            }
            return Room.databaseBuilder(
                applicationContext,
                LearningDatabase::class.java,
                databaseFile.name,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        }
    }
}
