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
}