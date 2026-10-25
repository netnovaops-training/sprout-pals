package org.jasonagibson.sproutpals.app

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
    /** A save that could not be read is kept here (once) instead of being silently replaced. */
    private val unreadableKey = stringPreferencesKey("save-unreadable")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun fresh() = Save(pet = PetEngine.newPet(System.currentTimeMillis(), Random.Default))

    private fun decode(raw: String?): Save? = raw?.let { runCatching { json.decodeFromString<Save>(it) }.getOrNull() }

    suspend fun load(): Save {
        val raw = context.dataStore.data.first()[key]
        decode(raw)?.let { return it }
        if (raw != null) keepUnreadable(raw)
        return fresh()
    }

    suspend fun save(save: Save) {
        context.dataStore.edit { it[key] = json.encodeToString(Save.serializer(), save) }
    }

    /**
     * Read, change and write the save in one locked step: [change] gets the save exactly as it is
     * on disk right now, so nothing written a moment earlier (a purchase in the app, say) can be
     * overwritten by an older copy. Returns the new save and whatever else [change] returns.
     *
     * The reminder job uses this. It used to load, work for a while, then save its whole copy back,
     * which undid any purchase made in between.
     */
    suspend fun <T> update(change: (Save) -> Pair<Save, T>): Pair<Save, T> {
        var out: Pair<Save, T>? = null
        context.dataStore.edit { prefs ->
            val raw = prefs[key]
            val current = decode(raw) ?: run {
                if (raw != null) keepUnreadableIn(prefs, raw)
                fresh()
            }
            val next = change(current)
            prefs[key] = json.encodeToString(Save.serializer(), next.first)
            out = next
        }
        return checkNotNull(out)
    }

    private suspend fun keepUnreadable(raw: String) {
        context.dataStore.edit { keepUnreadableIn(it, raw) }
    }

    private fun keepUnreadableIn(prefs: androidx.datastore.preferences.core.MutablePreferences, raw: String) {
        if (prefs[unreadableKey] == null) prefs[unreadableKey] = raw
    }

    /** For a test or a support look: is there a save that could not be read? */
    suspend fun hasUnreadable(): Boolean = context.dataStore.data.first().let { p: Preferences -> p[unreadableKey] != null }
}
