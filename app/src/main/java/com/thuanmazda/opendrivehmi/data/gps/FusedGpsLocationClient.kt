package com.thuanmazda.opendrivehmi.data.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FusedGpsLocationClient(
    context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context),
    private val locationManager: LocationManager = context.getSystemService(LocationManager::class.java),
    private val intervalMillis: Long = 1_000L,
    private val minUpdateIntervalMillis: Long = 500L,
) : GpsLocationClient {
    private val appContext = context.applicationContext

    override fun observeLocationUpdates(): Flow<GpsLocationEvent> = callbackFlow {
        if (!isLocationEnabled()) {
            trySend(GpsLocationEvent.LocationDisabled)
            close()
            return@callbackFlow
        }

        val finePermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (finePermission != PackageManager.PERMISSION_GRANTED && coarsePermission != PackageManager.PERMISSION_GRANTED) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateIntervalMillis)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    trySend(GpsLocationEvent.Fix(location.toGpsLocationSnapshot()))
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    trySend(GpsLocationEvent.ProviderUnavailable)
                }
            }
        }

        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (securityException: SecurityException) {
            close(securityException)
            return@callbackFlow
        }

        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(callback)
        }
    }

    override fun isLocationEnabled(): Boolean {
        return locationManager.isLocationEnabled
    }
}