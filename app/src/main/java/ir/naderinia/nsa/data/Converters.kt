package ir.naderinia.nsa.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromRepeatInterval(value: RepeatInterval): String = value.name

    @TypeConverter
    fun toRepeatInterval(value: String): RepeatInterval = RepeatInterval.valueOf(value)

    @TypeConverter
    fun fromFinancialType(value: FinancialType?): String? = value?.name

    @TypeConverter
    fun toFinancialType(value: String?): FinancialType? = value?.let { FinancialType.valueOf(it) }

    @TypeConverter
    fun fromCheckStatus(value: CheckStatus?): String? = value?.name

    @TypeConverter
    fun toCheckStatus(value: String?): CheckStatus? =
        value?.let { runCatching { CheckStatus.valueOf(it) }.getOrNull() }
}
