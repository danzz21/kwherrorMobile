package com.danzz.kwhmeter

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("kwh_meter")

object KwhPrefs {
    val DATA_JSON = stringPreferencesKey("data_json")
}

class AppDataStore(private val context: Context) {

    val dataFlow = context.dataStore.data.map { prefs ->
        prefs[KwhPrefs.DATA_JSON] ?: "[]"
    }

    suspend fun save(json: String) {
        context.dataStore.edit {
            it[KwhPrefs.DATA_JSON] = json
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(KwhPrefs.DATA_JSON)
        }
    }
}
