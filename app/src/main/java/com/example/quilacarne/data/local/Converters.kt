package com.example.quilacarne.data.local

import androidx.room.TypeConverter
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }
}