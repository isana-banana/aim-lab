package com.isanabanana.aim

import android.content.Context
import android.content.SharedPreferences

/**
 * Small, app-local persistent storage for scores and settings.
 *
 * This is not a database or cloud save system. Values are stored in Android
 * SharedPreferences and survive normal app restarts and process death.
 */
class GameData internal constructor(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences("aim_game_data", Context.MODE_PRIVATE)

    fun putInt(key: String, value: Int) { preferences.edit().putInt(validKey(key), value).apply() }
    fun putLong(key: String, value: Long) { preferences.edit().putLong(validKey(key), value).apply() }
    fun putFloat(key: String, value: Float) {
        require(value.isFinite()) { "Saved float must be finite." }
        preferences.edit().putFloat(validKey(key), value).apply()
    }
    fun putBoolean(key: String, value: Boolean) { preferences.edit().putBoolean(validKey(key), value).apply() }
    fun putString(key: String, value: String) { preferences.edit().putString(validKey(key), value).apply() }

    fun getInt(key: String, defaultValue: Int = 0): Int =
        preferences.getInt(validKey(key), defaultValue)
    fun getLong(key: String, defaultValue: Long = 0L): Long =
        preferences.getLong(validKey(key), defaultValue)
    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        preferences.getFloat(validKey(key), defaultValue)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        preferences.getBoolean(validKey(key), defaultValue)
    fun getString(key: String, defaultValue: String = ""): String =
        preferences.getString(validKey(key), defaultValue) ?: defaultValue

    fun contains(key: String): Boolean = preferences.contains(validKey(key))
    fun remove(key: String) { preferences.edit().remove(validKey(key)).apply() }
    fun clear() { preferences.edit().clear().apply() }

    private fun validKey(key: String): String = validateGameDataKey(key)
}

internal fun validateGameDataKey(key: String): String {
    require(key.isNotBlank()) { "Save key must not be blank." }
    return key
}
