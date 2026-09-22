package ir.naderinia.nsa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(entities = [Reminder::class, PaymentLog::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun paymentLogDao(): PaymentLogDao

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

        // Add one entry here per future schema change. Empty for now — the
        // current installed version (8) already matches this codebase's
        // schema, so nothing needs migrating yet.
        //
        // Template for next time (bump the @Database version above too):
        //
        // private val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        //     object : Migration(8, 9) {
        //         override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        //             db.execSQL("ALTER TABLE reminders ADD COLUMN newField TEXT")
        //         }
        //     }
        // )
        private val ALL_MIGRATIONS: Array<Migration> = arrayOf()
    }
}
