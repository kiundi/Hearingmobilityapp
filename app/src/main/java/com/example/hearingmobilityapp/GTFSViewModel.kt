package com.example.hearingmobilityapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class GTFSState {
    object Loading : GTFSState()
    object Success : GTFSState()
    data class Error(val message: String) : GTFSState()
    object Idle : GTFSState()
}

class GTFSViewModel(private val repository: GTFSRepository) : ViewModel() {
    private val _gtfsState = MutableStateFlow<GTFSState>(GTFSState.Idle)
    val gtfsState: StateFlow<GTFSState> = _gtfsState

    private val _isDataImported = MutableStateFlow(false)
    val isDataImported: StateFlow<Boolean> = _isDataImported

    fun importAllGTFSData(context: Context) {
        if (_isDataImported.value) return // Skip if already imported
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _gtfsState.value = GTFSState.Loading
                repository.importStopsFromGTFS(context)
                repository.importStopTimesFromGTFS(context)
                repository.importRoutesFromGTFS(context)
                _isDataImported.value = true
                _gtfsState.value = GTFSState.Success
            } catch (e: Exception) {
                _gtfsState.value = GTFSState.Error("Failed to import GTFS data: ${e.message}")
            }
        }
    }

    fun searchStops(query: String, onResult: (List<Stopentity>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stops = repository.searchStops(query)
                withContext(Dispatchers.Main) {
                    onResult(stops)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    fun getRoutesForStop(stopId: String, onResult: (List<RouteEntity>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val routes = repository.getRoutesForStop(stopId)
                withContext(Dispatchers.Main) {
                    onResult(routes)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    fun getTimesForStop(stopId: String, onResult: (List<StopTimeEntity>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val times = repository.getTimesForStop(stopId)
                withContext(Dispatchers.Main) {
                    onResult(times)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }
}
