package org.jasonagibson.sproutpals.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.jasonagibson.sproutpals.engine.PetEngine
import org.jasonagibson.sproutpals.engine.Save
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore(name = "sprout")

/**
 * The whole save is one JSON string. Simple to read, simple to back up, and the app
 * and the reminder job both go through here.
 */
class Store(private val context: Context) {
    private val key = stringPreferencesKey("save")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun load(): Save {
        val raw = context.dataStore.data.first()[key]
        return raw?.let { runCatching { json.decodeFromString<Save>(it) }.getOrNull() }
            ?: Save(pet = PetEngine.newPet(System.currentTimeMillis(), Random.Default))
    }

    suspend fun save(save: Save) {
        context.dataStore.edit { it[key] = json.encodeToString(Save.serializer(), save) }
    }
}
