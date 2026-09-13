package ir.naderinia.nsa.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY triggerAtMillis ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY triggerAtMillis ASC")
    fun observeUpcoming(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders")
    suspend fun getAllOnce(): List<Reminder>

    @Query("SELECT DISTINCT category FROM reminders ORDER BY category ASC")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM reminders WHERE amount IS NOT NULL ORDER BY triggerAtMillis ASC")
    fun observeFinancial(): Flow<List<Reminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
