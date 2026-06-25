package com.uninsubria.derma_bsa.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Patient::class, Session::class, Measurement::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun patientDao(): PatientDao
    abstract fun sessionDao(): SessionDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sessioni` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `patientId` INTEGER NOT NULL,
                        `dataOra` INTEGER NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `pazienti`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessioni_patientId` ON `sessioni` (`patientId`)"
                )
                database.execSQL("ALTER TABLE `misure` ADD COLUMN `sessionId` INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "INSERT INTO `sessioni` (patientId, dataOra) SELECT id, dataCreazione FROM `pazienti`"
                )
                database.execSQL("""
                    UPDATE `misure` SET `sessionId` = (
                        SELECT `id` FROM `sessioni`
                        WHERE `patientId` = `misure`.`patientId`
                        ORDER BY `dataOra` ASC LIMIT 1
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_misure_sessionId` ON `misure` (`sessionId`)"
                )
            }
        }

        // Aggiunge il campo dataNascita alla tabella pazienti
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `pazienti` ADD COLUMN `dataNascita` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "derma_bsa.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
