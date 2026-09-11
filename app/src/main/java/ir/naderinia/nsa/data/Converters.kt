package ir.naderinia.nsa.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromRepeatInterval(value: RepeatInterval): String = value.name

    @TypeConverter
    fun toRepeatInterval(value: String): RepeatInterval = RepeatInterval.valueOf(value)
}
