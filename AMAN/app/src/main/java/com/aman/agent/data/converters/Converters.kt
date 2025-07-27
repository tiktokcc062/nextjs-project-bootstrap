package com.aman.agent.data.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Converters - Type converters for Room database
 * Handles conversion of complex types to/from database storage
 */
class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return if (value == null) null else gson.toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return if (value == null) null else {
            val mapType = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(value, mapType)
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return if (value == null) null else gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value == null) null else {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType)
        }
    }

    @TypeConverter
    fun fromAnyMap(value: Map<String, Any>?): String? {
        return if (value == null) null else gson.toJson(value)
    }

    @TypeConverter
    fun toAnyMap(value: String?): Map<String, Any>? {
        return if (value == null) null else {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(value, mapType)
        }
    }
}
