package com.petcare.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.petcare.app.model.CareTask
import com.petcare.app.model.Expense
import com.petcare.app.model.Pet
import com.petcare.app.model.PetLocation
import com.petcare.app.model.SmsReminderOccurrence
import com.petcare.app.model.SmsReminderPart
import com.petcare.app.model.User

@Database(
    entities = [User::class, Pet::class, CareTask::class, Expense::class, PetLocation::class, SmsReminderOccurrence::class, SmsReminderPart::class],
    version = 6,
    exportSchema = true
)
abstract class PetCareDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun petDao(): PetDao
    abstract fun careTaskDao(): CareTaskDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun petLocationDao(): PetLocationDao
    abstract fun smsReminderOccurrenceDao(): SmsReminderOccurrenceDao

    companion object {
        @Volatile
        private var instance: PetCareDatabase? = null

        /** v1 → v2: add phoneNumber to users; create expenses and pet_locations tables. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE users ADD COLUMN phoneNumber TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerId` INTEGER NOT NULL,
                        `petId` INTEGER,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `dateEpochDay` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`ownerId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_expenses_ownerId` ON `expenses` (`ownerId`)"
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `pet_locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `address` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `latitude` REAL,
                        `longitude` REAL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`ownerId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pet_locations_ownerId` ON `pet_locations` (`ownerId`)"
                )
            }
        }

        /** v2 → v3: add photoPath column to users for profile photos. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE users ADD COLUMN photoPath TEXT"
                )
            }
        }

        /** Preserve existing accounts/photos/tasks; new reminders are opt-in.
         * Legacy locations remain stored for safe upgrades, but are not exposed
         * by the gestures-only assignment application. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pets ADD COLUMN galleryPhotoPaths TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE care_tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 → v5: automatic SMS remains disabled for every installed task. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE care_tasks ADD COLUMN smsReminderEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE care_tasks ADD COLUMN smsRecipient TEXT NOT NULL DEFAULT ''")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sms_reminder_occurrences` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ownerId` INTEGER NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `dueEpochDay` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `detail` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_reminder_occurrences_ownerId_taskId_dueEpochDay`
                       ON `sms_reminder_occurrences` (`ownerId`, `taskId`, `dueEpochDay`)"""
                )
            }
        }

        /** v5 → v6: retain individual multipart sent callbacks safely. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sms_reminder_occurrences ADD COLUMN expectedParts INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sms_reminder_parts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `occurrenceId` INTEGER NOT NULL,
                        `partIndex` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_reminder_parts_occurrenceId_partIndex`
                       ON `sms_reminder_parts` (`occurrenceId`, `partIndex`)"""
                )
            }
        }

        fun getInstance(context: Context): PetCareDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PetCareDatabase::class.java,
                    "petcare.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
