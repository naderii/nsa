package ir.naderinia.nsa.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {

    @Insert
    suspend fun insert(log: MedicationLog)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertFromBackup(log: MedicationLog)

    @Query("SELECT * FROM medication_logs")
    suspend fun getAllOnce(): List<MedicationLog>

    @Query("SELECT * FROM medication_logs ORDER BY takenAtMillis DESC")
    fun observeAll(): Flow<List<MedicationLog>>
}
