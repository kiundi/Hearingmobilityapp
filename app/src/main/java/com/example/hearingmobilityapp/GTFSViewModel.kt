package com.example.hearingmobilityapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.hearingmobilityapp.GTFSDatabase
import com.example.hearingmobilityapp.GTFSRepository
import com.example.hearingmobilityapp.StopEntity
import com.example.hearingmobilityapp.RouteEntity
import com.example.hearingmobilityapp.StopTimeEntity
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class GTFSViewModel(application: Application) : AndroidViewModel(application) {
    private val database: GTFSDatabase = GTFSDatabase.getDatabase(application)
    private val repository: GTFSRepository = GTFSRepository(database)
    
    private val _isDataLoaded = MutableLiveData<Boolean>(false)
    val isDataLoaded: LiveData<Boolean> = _isDataLoaded
    
    private val _dataLoadingError = MutableLiveData<String?>(null)
    val dataLoadingError: LiveData<String?> = _dataLoadingError
    
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadGTFSData()
    }

    fun loadGTFSData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _dataLoadingError.value = null
                
                Log.d("GTFSViewModel", "Starting to load GTFS data")
                withTimeout(30000) { // 30 second timeout
                    repository.loadGTFSData(getApplication())
                }
                
                _isDataLoaded.value = true
                Log.d("GTFSViewModel", "GTFS data loaded successfully")
            } catch (e: TimeoutCancellationException) {
                Log.e("GTFSViewModel", "Timeout loading GTFS data", e)
                _dataLoadingError.value = "Loading timed out. Please try again."
                _isDataLoaded.value = false
            } catch (e: Exception) {
                Log.e("GTFSViewModel", "Error loading GTFS data: ${e.message}", e)
                _dataLoadingError.value = e.message ?: "Unknown error occurred"
                _isDataLoaded.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchStops(query: String): Flow<List<StopEntity>> {
        return repository.searchStops(query)
            .catch { e ->
                Log.e("GTFSViewModel", "Error searching stops: ${e.message}", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    fun getRoutesForStop(stopId: String): Flow<List<RouteEntity>> {
        return repository.getRoutesForStop(stopId)
            .catch { e ->
                Log.e("GTFSViewModel", "Error getting routes for stop: ${e.message}", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    fun getTimesForStop(stopId: String): Flow<List<StopTimeEntity>> {
        return repository.getTimesForStop(stopId)
            .catch { e ->
                Log.e("GTFSViewModel", "Error getting times for stop: ${e.message}", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GTFSViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GTFSViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
