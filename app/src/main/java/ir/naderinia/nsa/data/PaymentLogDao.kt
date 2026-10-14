package ir.naderinia.nsa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentLogDao {

    @Insert
    suspend fun insert(log: PaymentLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFromBackup(log: PaymentLog)

    @Query("SELECT * FROM payment_logs ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<PaymentLog>>

    @Query("SELECT * FROM payment_logs")
    suspend fun getAllOnce(): List<PaymentLog>

    @Query("SELECT * FROM payment_logs WHERE reminderId = :reminderId ORDER BY timestampMillis DESC, id DESC LIMIT 1")
    suspend fun lastForReminder(reminderId: Long): PaymentLog?

    @Query("DELETE FROM payment_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
