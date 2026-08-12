package com.thuanmazda.opendrivehmi.data.gps

import android.os.Build
import android.util.Log
import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleConnectionStatus
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleValue
import com.thuanmazda.opendrivehmi.domain.vehicle.source.ConnectionState
import com.thuanmazda.opendrivehmi.domain.vehicle.source.VehicleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class GpsDataSource(
    private val locationClient: GpsLocationClient,
    private val permissionState: StateFlow<LocationPermissionState>,
    private val coroutineScope: CoroutineScope,
    private val staleLocationThreshold: Duration = Duration.ofSeconds(10),
    private val lowAccuracyThresholdMeters: Float = 50f,
) : VehicleDataSource {
    override val type = DataSourceType.GPS
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val vehicleState = MutableStateFlow<VehicleState?>(null)
    override val navigationState = MutableStateFlow<NavigationState?>(null)

    private val mapper = GpsVehicleStateMapper()
    private var permissionJob: Job? = null
    private var observationJob: Job? = null

    override suspend fun connect() {
        startMonitoring()
    }

    override suspend fun disconnect() {
        stopObservation()
        permissionJob?.cancel()
        permissionJob = null
        connectionState.value = ConnectionState.DISCONNECTED
        vehicleState.value = null
        logDebug("GPS disconnected")
    }

    override suspend fun reconnect() {
        disconnect()
        connect()
    }

    private fun startMonitoring() {
        if (permissionJob?.isActive == true) {
            return
        }

        permissionJob = coroutineScope.launch {
            permissionState.collectLatest { permission ->
                when (permission) {
                    LocationPermissionState.GRANTED -> startObservationIfNeeded()
                    LocationPermissionState.DENIED -> handlePermissionFailure("denied")
                    LocationPermissionState.REVOKED -> handlePermissionFailure("revoked")
                }
            }
        }
    }

    private suspend fun startObservationIfNeeded() {
        if (observationJob?.isActive == true) {
            return
        }

        if (!locationClient.isLocationEnabled()) {
            connectionState.value = ConnectionState.DISCONNECTED
            vehicleState.value = null
            logDebug("GPS location disabled")
            return
        }

        connectionState.value = ConnectionState.CONNECTING
        observationJob = coroutineScope.launch {
            try {
                locationClient.observeLocationUpdates().collect { event ->
                    when (event) {
                        is GpsLocationEvent.Fix -> handleFix(event.snapshot)
                        GpsLocationEvent.LocationDisabled -> handleTerminalLocationFailure(ConnectionState.DISCONNECTED, "location disabled")
                        GpsLocationEvent.ProviderUnavailable -> handleTerminalLocationFailure(ConnectionState.DISCONNECTED, "provider unavailable")
                    }
                }
            } catch (cancellation: CancellationException) {
                connectionState.value = ConnectionState.DISCONNECTED
                logDebug("GPS observation cancelled")
                throw cancellation
            } catch (securityException: SecurityException) {
                handlePermissionFailure("security exception: ${securityException.message}")
            } catch (throwable: Throwable) {
                connectionState.value = ConnectionState.FAILED
                vehicleState.value = null
                logError("GPS observation failed", throwable)
            } finally {
                observationJob = null
            }
        }
    }

    private fun handleFix(snapshot: GpsLocationSnapshot) {
        val mappedState = mapper.map(snapshot)
        val nowMillis = Instant.now().toEpochMilli()
        val ageMillis = nowMillis - snapshot.timestampMillis
        val stale = ageMillis > staleLocationThreshold.toMillis()
        val lowAccuracy = snapshot.accuracyMeters != null && snapshot.accuracyMeters > lowAccuracyThresholdMeters

        vehicleState.value = when {
            stale || lowAccuracy -> mappedState.copy(
                vehicleConnectionStatus = VehicleValue(
                    value = VehicleConnectionStatus.DEGRADED,
                    source = DataSourceType.GPS,
                    available = true,
                    timestamp = Instant.ofEpochMilli(snapshot.timestampMillis),
                ),
            )
            else -> mappedState
        }

        connectionState.value = when {
            stale || lowAccuracy -> ConnectionState.DEGRADED
            else -> ConnectionState.CONNECTED
        }

        if (stale) {
            logDebug("GPS fix is stale: ageMillis=$ageMillis")
        }
        if (lowAccuracy) {
            logDebug("GPS fix low accuracy: accuracyMeters=${snapshot.accuracyMeters}")
        }
    }

    private fun handlePermissionFailure(reason: String) {
        stopObservation()
        connectionState.value = ConnectionState.FAILED
        vehicleState.value = null
        logDebug("GPS permission $reason")
    }

    private fun handleTerminalLocationFailure(state: ConnectionState, reason: String) {
        stopObservation()
        connectionState.value = state
        vehicleState.value = null
        logDebug("GPS $reason")
    }

    private fun stopObservation() {
        observationJob?.cancel()
        observationJob = null
    }

    private fun logDebug(message: String) {
        if (Build.TYPE != "user") {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable) {
        if (Build.TYPE != "user") {
            Log.e(TAG, message, throwable)
        }
    }

    private companion object {
        private const val TAG = "GpsDataSource"
    }
}