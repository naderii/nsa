package ir.naderinia.nsa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One tap on "خوردم" — a dose that was actually taken. */
@Entity(tableName = "medication_logs")
data class MedicationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val takenAtMillis: Long = System.currentTimeMillis()
)
