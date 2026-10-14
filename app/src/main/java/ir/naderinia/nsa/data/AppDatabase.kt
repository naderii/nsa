package ir.naderinia.nsa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(entities = [Reminder::class, PaymentLog::class, MedicationLog::class], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun paymentLogDao(): PaymentLogDao
    abstract fun medicationLogDao(): MedicationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nsa.db"
                )
                    // No destructive fallback anymore: this is the schema
                    // baseline for the public release, so anyone's existing
                    // data must survive every future update. From this point
                    // on, ANY change to Reminder/PaymentLog (a new column, a
                    // new table, a renamed field) MUST ship together with:
                    //   1. version = <n+1> above, and
                    //   2. a new Migration(n, n+1) added to ALL_MIGRATIONS
                    //      below, with the matching ALTER TABLE / CREATE TABLE.
                    // Skipping this makes Room crash loudly on the next
                    // update instead of silently wiping the person's data —
                    // which is the safe failure mode we want.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build().also { INSTANCE = it }
            }
        }

        // Add one entry here per future schema change.
        //
        // Template for next time (bump the @Database version above too):
        //
        // private val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        //     object : Migration(9, 10) {
        //         override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        //             db.execSQL("ALTER TABLE reminders ADD COLUMN newField TEXT")
        //         }
        //     }
        // )
        private val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(8, 9) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Performance indices — no data changes, just faster lookups
                    // on the columns we filter by most (category, financial type,
                    // due date).
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_category ON reminders(category)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_financialType ON reminders(financialType)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_triggerAtMillis ON reminders(triggerAtMillis)")
                }
            },
            object : Migration(9, 10) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // "سرویس خودرو" was renamed to "سرویس وسیله نقلیه" so it also
                    // covers motorcycles — this is a pure data fix (no column
                    // change), updating existing rows to the new category name
                    // so they still show up under the renamed tile/filter.
                    db.execSQL("UPDATE reminders SET category = 'وسیله نقلیه' WHERE category = 'ماشین'")
                }
            },
            object : Migration(10, 11) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Cheque status (پاس شد / برگشت خورد / نقد شد). Nullable, so
                    // every existing row simply starts as "pending".
                    db.execSQL("ALTER TABLE reminders ADD COLUMN checkStatus TEXT")
                }
            },
            object : Migration(11, 12) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Birthday year, checklist, and the "took it" log for medication.
                    // New columns are nullable, so existing reminders are untouched.
                    db.execSQL("ALTER TABLE reminders ADD COLUMN birthYear INTEGER")
                    db.execSQL("ALTER TABLE reminders ADD COLUMN checklist TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS medication_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "reminderId INTEGER NOT NULL, " +
                            "takenAtMillis INTEGER NOT NULL)"
                    )
                }
            },
            object : Migration(12, 13) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Day-of-month anchor for monthly/yearly repeats (see Reminder.repeatAnchorDay).
                    // Nullable: old rows keep working and get anchored the next time they advance.
                    db.execSQL("ALTER TABLE reminders ADD COLUMN repeatAnchorDay INTEGER")
                }
            }
        )
    }
}