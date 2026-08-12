package com.thuanmazda.opendrivehmi.domain.vehicle.source

import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
    DEGRADED,
}

interface VehicleDataSource {
    val type: DataSourceType
    val connectionState: StateFlow<ConnectionState>
    val vehicleState: StateFlow<VehicleState?>
    val navigationState: StateFlow<NavigationState?>

    suspend fun connect()

    suspend fun disconnect()

    suspend fun reconnect() {
        disconnect()
        connect()
    }
}

enum class DataSourceMode {
    GPS,
    ETS2,
    DEMO,
    AUTO,
    HYBRID,
}

data class VehicleFieldSourceConfiguration(
    val speed: DataSourceType? = null,
    val speedUnit: DataSourceType? = null,
    val latitude: DataSourceType? = null,
    val longitude: DataSourceType? = null,
    val altitude: DataSourceType? = null,
    val accuracy: DataSourceType? = null,
    val heading: DataSourceType? = null,
    val gear: DataSourceType? = null,
    val rpm: DataSourceType? = null,
    val fuelLevel: DataSourceType? = null,
    val fuelConsumption: DataSourceType? = null,
    val engineTemperature: DataSourceType? = null,
    val turnSignal: DataSourceType? = null,
    val lights: DataSourceType? = null,
    val parkingBrake: DataSourceType? = null,
    val cruiseControl: DataSourceType? = null,
    val cruiseControlSpeed: DataSourceType? = null,
    val engineBrake: DataSourceType? = null,
    val trailerAttached: DataSourceType? = null,
    val vehicleConnectionStatus: DataSourceType? = null,
)

data class VehicleSourceConfiguration(
    val mode: DataSourceMode = DataSourceMode.AUTO,
    val allowDemoFallbackInAuto: Boolean = false,
    val vehicleFieldSources: VehicleFieldSourceConfiguration = VehicleFieldSourceConfiguration(),
    val navigationSource: DataSourceType = DataSourceType.GPS,
)

interface DataSourceManager {
    val configuration: StateFlow<VehicleSourceConfiguration>
    val activeSourceType: StateFlow<DataSourceType>
    val connectionState: StateFlow<ConnectionState>
    val vehicleState: StateFlow<VehicleState?>
    val navigationState: StateFlow<NavigationState?>

    suspend fun updateConfiguration(configuration: VehicleSourceConfiguration)
    suspend fun connect()
    suspend fun disconnect()
    suspend fun reconnect()
}

class DefaultDataSourceManager(
    private val sources: Map<DataSourceType, VehicleDataSource>,
    initialConfiguration: VehicleSourceConfiguration = VehicleSourceConfiguration(),
    coroutineScope: CoroutineScope,
) : DataSourceManager {
    private val configurationFlow = MutableStateFlow(initialConfiguration)

    override val configuration: StateFlow<VehicleSourceConfiguration> = configurationFlow
    override val activeSourceType = MutableStateFlow(DataSourceType.NONE)
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val vehicleState = MutableStateFlow<VehicleState?>(null)
    override val navigationState = MutableStateFlow<NavigationState?>(null)

    init {
        coroutineScope.launch {
            configurationFlow.collect {
                recomputeState()
            }
        }

        sources.values.forEach { source ->
            coroutineScope.launch {
                source.connectionState.collect {
                    recomputeState()
                }
            }
            coroutineScope.launch {
                source.vehicleState.collect {
                    recomputeState()
                }
            }
            coroutineScope.launch {
                source.navigationState.collect {
                    recomputeState()
                }
            }
        }

        recomputeState()
    }

    override suspend fun updateConfiguration(configuration: VehicleSourceConfiguration) {
        configurationFlow.value = configuration
        recomputeState()
    }

    override suspend fun connect() {
        requiredSources(configurationFlow.value).forEach { sourceType ->
            sources[sourceType]?.connect()
        }
        recomputeState()
    }

    override suspend fun disconnect() {
        sources.values.forEach { source ->
            source.disconnect()
        }
        recomputeState()
    }

    override suspend fun reconnect() {
        requiredSources(configurationFlow.value).forEach { sourceType ->
            sources[sourceType]?.reconnect()
        }
        recomputeState()
    }

    private fun recomputeState() {
        val configuration = configurationFlow.value
        activeSourceType.value = resolveActiveSourceType(configuration)
        connectionState.value = resolveConnectionState(configuration)
        vehicleState.value = resolveVehicleState(configuration)
        navigationState.value = resolveNavigationState(configuration)
    }

    private fun resolveActiveSourceType(configuration: VehicleSourceConfiguration): DataSourceType {
        return when (configuration.mode) {
            DataSourceMode.GPS -> DataSourceType.GPS
            DataSourceMode.ETS2 -> DataSourceType.ETS2
            DataSourceMode.DEMO -> DataSourceType.DEMO
            DataSourceMode.AUTO -> autoCandidates(configuration).firstOrNull { isConnected(it) }
                ?: autoCandidates(configuration).firstOrNull()
                ?: DataSourceType.NONE
            DataSourceMode.HYBRID -> hybridCandidates(configuration).firstOrNull { isConnected(it) }
                ?: hybridCandidates(configuration).firstOrNull()
                ?: DataSourceType.NONE
        }
    }

    private fun resolveConnectionState(configuration: VehicleSourceConfiguration): ConnectionState {
        val requiredSources = requiredSources(configuration)
        val connectionStates = requiredSources.mapNotNull { sourceType ->
            sources[sourceType]?.connectionState?.value
        }

        if (connectionStates.isEmpty()) {
            return ConnectionState.DISCONNECTED
        }

        if (connectionStates.any { it == ConnectionState.FAILED }) {
            return ConnectionState.FAILED
        }

        if (connectionStates.any { it == ConnectionState.CONNECTING || it == ConnectionState.RECONNECTING }) {
            return if (configuration.mode == DataSourceMode.HYBRID && connectionStates.any { it == ConnectionState.CONNECTED }) {
                ConnectionState.DEGRADED
            } else {
                ConnectionState.CONNECTING
            }
        }

        val connectedCount = connectionStates.count { it == ConnectionState.CONNECTED }
        return when {
            connectedCount == requiredSources.size -> ConnectionState.CONNECTED
            configuration.mode == DataSourceMode.HYBRID && connectedCount > 0 -> ConnectionState.DEGRADED
            connectedCount > 0 -> ConnectionState.CONNECTED
            else -> ConnectionState.DISCONNECTED
        }
    }

    private fun resolveVehicleState(configuration: VehicleSourceConfiguration): VehicleState? {
        return when (configuration.mode) {
            DataSourceMode.GPS -> buildVehicleState(listOf(DataSourceType.GPS))
            DataSourceMode.ETS2 -> buildVehicleState(listOf(DataSourceType.ETS2))
            DataSourceMode.DEMO -> buildVehicleState(listOf(DataSourceType.DEMO))
            DataSourceMode.AUTO -> buildVehicleState(autoCandidates(configuration))
            DataSourceMode.HYBRID -> buildHybridVehicleState(configuration)
        }
    }

    private fun resolveNavigationState(configuration: VehicleSourceConfiguration): NavigationState? {
        return when (configuration.mode) {
            DataSourceMode.GPS -> selectNavigationState(listOf(DataSourceType.GPS))
            DataSourceMode.ETS2 -> selectNavigationState(listOf(DataSourceType.ETS2))
            DataSourceMode.DEMO -> selectNavigationState(listOf(DataSourceType.DEMO))
            DataSourceMode.AUTO -> selectNavigationState(autoCandidates(configuration))
            DataSourceMode.HYBRID -> selectNavigationState(listOf(configuration.navigationSource))
        }
    }

    private fun buildVehicleState(candidates: List<DataSourceType>): VehicleState? {
        if (candidates.isEmpty()) {
            return null
        }

        return VehicleState(
            speed = selectField(candidates, VehicleState::speed),
            speedUnit = selectField(candidates, VehicleState::speedUnit),
            latitude = selectField(candidates, VehicleState::latitude),
            longitude = selectField(candidates, VehicleState::longitude),
            altitude = selectField(candidates, VehicleState::altitude),
            accuracy = selectField(candidates, VehicleState::accuracy),
            heading = selectField(candidates, VehicleState::heading),
            gear = selectField(candidates, VehicleState::gear),
            rpm = selectField(candidates, VehicleState::rpm),
            fuelLevel = selectField(candidates, VehicleState::fuelLevel),
            fuelConsumption = selectField(candidates, VehicleState::fuelConsumption),
            engineTemperature = selectField(candidates, VehicleState::engineTemperature),
            turnSignal = selectField(candidates, VehicleState::turnSignal),
            lights = selectField(candidates, VehicleState::lights),
            parkingBrake = selectField(candidates, VehicleState::parkingBrake),
            cruiseControl = selectField(candidates, VehicleState::cruiseControl),
            cruiseControlSpeed = selectField(candidates, VehicleState::cruiseControlSpeed),
            engineBrake = selectField(candidates, VehicleState::engineBrake),
            trailerAttached = selectField(candidates, VehicleState::trailerAttached),
            vehicleConnectionStatus = selectField(candidates, VehicleState::vehicleConnectionStatus),
        )
    }

    private fun buildHybridVehicleState(configuration: VehicleSourceConfiguration): VehicleState? {
        val selection = configuration.vehicleFieldSources
        return VehicleState(
            speed = selectField(listOfNotNull(selection.speed), VehicleState::speed),
            speedUnit = selectField(listOfNotNull(selection.speedUnit), VehicleState::speedUnit),
            latitude = selectField(listOfNotNull(selection.latitude), VehicleState::latitude),
            longitude = selectField(listOfNotNull(selection.longitude), VehicleState::longitude),
            altitude = selectField(listOfNotNull(selection.altitude), VehicleState::altitude),
            accuracy = selectField(listOfNotNull(selection.accuracy), VehicleState::accuracy),
            heading = selectField(listOfNotNull(selection.heading), VehicleState::heading),
            gear = selectField(listOfNotNull(selection.gear), VehicleState::gear),
            rpm = selectField(listOfNotNull(selection.rpm), VehicleState::rpm),
            fuelLevel = selectField(listOfNotNull(selection.fuelLevel), VehicleState::fuelLevel),
            fuelConsumption = selectField(listOfNotNull(selection.fuelConsumption), VehicleState::fuelConsumption),
            engineTemperature = selectField(listOfNotNull(selection.engineTemperature), VehicleState::engineTemperature),
            turnSignal = selectField(listOfNotNull(selection.turnSignal), VehicleState::turnSignal),
            lights = selectField(listOfNotNull(selection.lights), VehicleState::lights),
            parkingBrake = selectField(listOfNotNull(selection.parkingBrake), VehicleState::parkingBrake),
            cruiseControl = selectField(listOfNotNull(selection.cruiseControl), VehicleState::cruiseControl),
            cruiseControlSpeed = selectField(listOfNotNull(selection.cruiseControlSpeed), VehicleState::cruiseControlSpeed),
            engineBrake = selectField(listOfNotNull(selection.engineBrake), VehicleState::engineBrake),
            trailerAttached = selectField(listOfNotNull(selection.trailerAttached), VehicleState::trailerAttached),
            vehicleConnectionStatus = selectField(listOfNotNull(selection.vehicleConnectionStatus), VehicleState::vehicleConnectionStatus),
        )
    }

    private fun selectNavigationState(candidates: List<DataSourceType>): NavigationState? {
        for (sourceType in candidates) {
            val source = sources[sourceType] ?: continue
            if (source.connectionState.value != ConnectionState.CONNECTED) {
                continue
            }

            val navigationState = source.navigationState.value
            if (navigationState != null) {
                return navigationState
            }
        }
        return null
    }

    private inline fun <T> selectField(
        candidates: List<DataSourceType>,
        selector: (VehicleState) -> VehicleValue<T>,
    ): VehicleValue<T> {
        var fallbackSource = candidates.firstOrNull() ?: DataSourceType.NONE
        var fallbackTimestamp = Instant.EPOCH

        for (sourceType in candidates) {
            val source = sources[sourceType] ?: continue
            val state = source.vehicleState.value ?: continue
            val value = selector(state)
            fallbackSource = sourceType
            fallbackTimestamp = value.timestamp

            if (source.connectionState.value == ConnectionState.CONNECTED && value.available) {
                return value
            }
        }

        return VehicleValue(
            value = null,
            source = fallbackSource,
            available = false,
            timestamp = fallbackTimestamp,
        )
    }

    private fun autoCandidates(configuration: VehicleSourceConfiguration): List<DataSourceType> {
        return buildList {
            add(DataSourceType.ETS2)
            add(DataSourceType.GPS)
            if (configuration.allowDemoFallbackInAuto) {
                add(DataSourceType.DEMO)
            }
        }.distinct()
    }

    private fun hybridCandidates(configuration: VehicleSourceConfiguration): List<DataSourceType> {
        return buildList {
            configuration.vehicleFieldSources.speed?.let(::add)
            configuration.vehicleFieldSources.speedUnit?.let(::add)
            configuration.vehicleFieldSources.latitude?.let(::add)
            configuration.vehicleFieldSources.longitude?.let(::add)
            configuration.vehicleFieldSources.altitude?.let(::add)
            configuration.vehicleFieldSources.accuracy?.let(::add)
            configuration.vehicleFieldSources.heading?.let(::add)
            configuration.vehicleFieldSources.gear?.let(::add)
            configuration.vehicleFieldSources.rpm?.let(::add)
            configuration.vehicleFieldSources.fuelLevel?.let(::add)
            configuration.vehicleFieldSources.fuelConsumption?.let(::add)
            configuration.vehicleFieldSources.engineTemperature?.let(::add)
            configuration.vehicleFieldSources.turnSignal?.let(::add)
            configuration.vehicleFieldSources.lights?.let(::add)
            configuration.vehicleFieldSources.parkingBrake?.let(::add)
            configuration.vehicleFieldSources.cruiseControl?.let(::add)
            configuration.vehicleFieldSources.cruiseControlSpeed?.let(::add)
            configuration.vehicleFieldSources.engineBrake?.let(::add)
            configuration.vehicleFieldSources.trailerAttached?.let(::add)
            configuration.vehicleFieldSources.vehicleConnectionStatus?.let(::add)
            add(configuration.navigationSource)
        }.distinct()
    }

    private fun requiredSources(configuration: VehicleSourceConfiguration): List<DataSourceType> {
        return when (configuration.mode) {
            DataSourceMode.GPS -> listOf(DataSourceType.GPS)
            DataSourceMode.ETS2 -> listOf(DataSourceType.ETS2)
            DataSourceMode.DEMO -> listOf(DataSourceType.DEMO)
            DataSourceMode.AUTO -> autoCandidates(configuration)
            DataSourceMode.HYBRID -> hybridCandidates(configuration)
        }
    }

    private fun isConnected(sourceType: DataSourceType): Boolean {
        return sources[sourceType]?.connectionState?.value == ConnectionState.CONNECTED
    }
}
