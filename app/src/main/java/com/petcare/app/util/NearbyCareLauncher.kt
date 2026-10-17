package com.petcare.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a location-independent search for nearby pet care. The app does not
 * request location access or manufacture a list of clinics; Maps/browser
 * providers decide what results to show.
 */
enum class NearbyCareType(
    val label: String,
    val searchQuery: String
) {
    PET_PHARMACIES("Pet pharmacies", "pet pharmacies near me"),
    VETERINARIANS("Vets", "veterinarians near me"),
    ANIMAL_HOSPITALS("Animal hospitals", "animal hospitals near me")
}

object NearbyCareLauncher {
    fun mapIntent(type: NearbyCareType): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(type.searchQuery)}")
        )

    fun browserIntent(type: NearbyCareType): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(type.searchQuery)}")
        )

    /**
     * Tries a Maps-style geo search first, then an HTTPS browser search. The
     * callback is required so a device with no handler has visible feedback.
     */
    fun open(
        context: Context,
        type: NearbyCareType,
        onFailure: (String) -> Unit
    ): Boolean {
        val map = mapIntent(type)
        // Do not call resolveActivity here. Android 11 package visibility can
        // hide an otherwise valid Maps handler unless the manifest declares
        // every matching query. Starting directly is both more reliable and
        // keeps this feature independent of the SMS worker's manifest query.
        if (start(context, map)) return true

        val browser = browserIntent(type)
        if (start(context, browser)) return true

        onFailure("No Maps or browser app is available to search for ${type.label.lowercase()}.")
        return false
    }

    private fun start(context: Context, intent: Intent): Boolean =
        try {
            val launchIntent = if (context is Activity) {
                intent
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}