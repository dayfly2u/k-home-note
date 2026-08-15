package com.khomenote.data.local

import androidx.room.TypeConverter
import com.khomenote.data.local.entity.AssetCategory
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromCategoryName(name: String?): AssetCategory? = name?.let(AssetCategory::valueOf)

    @TypeConverter
    fun toCategoryName(category: AssetCategory?): String? = category?.name
}
