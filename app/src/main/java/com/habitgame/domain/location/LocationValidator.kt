package com.habitgame.domain.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.habitgame.core.database.entity.LocationZoneEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LocationValidationResult(
    val isInZone: Boolean,
    val distanceMeters: Float,
    val zone: LocationZoneEntity
)

@Singleton
class LocationValidator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        }

    suspend fun validateZone(zone: LocationZoneEntity): LocationValidationResult {
        val current = getCurrentLocation()
        return if (current == null) {
            LocationValidationResult(false, Float.MAX_VALUE, zone)
        } else {
            val results = FloatArray(1)
            Location.distanceBetween(
                current.latitude, current.longitude,
                zone.latitude, zone.longitude,
                results
            )
            val distance = results[0]
            LocationValidationResult(
                isInZone = distance <= zone.radiusMeters,
                distanceMeters = distance,
                zone = zone
            )
        }
    }
}
