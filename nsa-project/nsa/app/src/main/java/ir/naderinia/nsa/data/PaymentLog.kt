package ir.naderinia.nsa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_logs")
data class PaymentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val amount: Long,
    val timestampMillis: Long = System.currentTimeMillis()
)
