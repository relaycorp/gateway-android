package tech.relaycorp.gateway.ui.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import tech.relaycorp.gateway.background.ConnectionState
import tech.relaycorp.gateway.background.ConnectionStateObserver
import tech.relaycorp.gateway.data.model.StorageSize
import tech.relaycorp.gateway.domain.GetEndpointApplicationsCount
import tech.relaycorp.gateway.domain.GetTotalOutgoingData
import tech.relaycorp.gateway.ui.BaseViewModel
import javax.inject.Inject

class MainViewModel
@Inject constructor(
    private val connectionStateObserver: ConnectionStateObserver,
    private val getTotalOutgoingData: GetTotalOutgoingData,
    private val getEndpointApplicationsCount: GetEndpointApplicationsCount
) : BaseViewModel() {

    // Outputs

    val connectionState get() = connectionStateObserver.observe()

    val dataState: Flow<DataState> get() = _dataState
    private val _dataState = MutableStateFlow<DataState>(DataState.Invisible)
    val appsState: Flow<AppsState> get() = _appsState
    private val _appsState = MutableStateFlow<AppsState>(AppsState.None)

    val isCourierSyncVisible
        get() = connectionStateObserver.observe()
            .map { it !is ConnectionState.InternetAndPublicGateway }

    init {
        connectionStateObserver
            .observe()
            .flatMapLatest { connectionState ->
                getTotalOutgoingData
                    .get()
                    .map { outgoingData ->
                        when (connectionState) {
                            is ConnectionState.InternetAndPublicGateway -> DataState.Invisible
                            else ->
                                if (outgoingData.isZero) {
                                    DataState.Visible.WithoutOutgoingData
                                } else {
                                    DataState.Visible.WithOutgoingData(outgoingData)
                                }
                        }
                    }
            }
            .onEach { _dataState.value = it }
            .launchIn(ioScope)

        getEndpointApplicationsCount.get()
            .map { if (it > 0) AppsState.Some else AppsState.None }
            .onEach { _appsState.value = it }
            .launchIn(ioScope)
    }

    sealed class DataState {
        object Invisible : DataState()
        sealed class Visible : DataState() {
            data class WithOutgoingData(val dataWaitingToSync: StorageSize) : Visible()
            object WithoutOutgoingData : Visible()
        }
    }

    enum class AppsState {
        Some, None
    }
}
