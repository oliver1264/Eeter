package com.eeter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "eeradio")

/**
 * Favorites persistence. Seeds with the canonical default favorites on first run;
 * after that the user's toggles are honored.
 */
class FavoritesStore(private val context: Context) {

    private val key = stringSetPreferencesKey("favorite_ids")
    private val orderKey = stringPreferencesKey("favorite_order")

    /** Favorite station ids, in the user's saved order (canonical order as fallback), as a Flow. */
    val favoriteIds: Flow<List<Int>> = context.dataStore.data.map { prefs ->
        val saved = prefs[key]
        val ids = saved?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: Stations.defaultFavorites().map { it.id }.toSet()
        // The drag-saved grid order wins; canonical order slots in anything not covered
        // by it (e.g. favorites added after the last drag), then any remaining extras.
        val custom = prefs[orderKey]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        val base = custom + Stations.defaultFavorites().map { it.id }.filter { it !in custom }
        val ordered = base.filter { it in ids }
        ordered + ids.filter { it !in ordered }
    }

    /** Persists the grid order chosen by dragging tiles (first id = top-left tile). */
    suspend fun setOrder(ids: List<Int>) {
        context.dataStore.edit { prefs -> prefs[orderKey] = ids.joinToString(",") }
    }

    suspend fun toggle(id: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.toMutableSet()
                ?: Stations.defaultFavorites().map { it.id.toString() }.toMutableSet()
            val s = id.toString()
            if (!current.remove(s)) current.add(s)
            prefs[key] = current
        }
    }
}
