package com.example.hearingmobilityapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.example.hearingmobilityapp.GTFSDatabase
import com.example.hearingmobilityapp.GTFSRepository
import com.example.hearingmobilityapp.StopEntity
import com.example.hearingmobilityapp.RouteEntity
import com.example.hearingmobilityapp.StopTimeEntity

class GTFSViewModel(application: Application) : AndroidViewModel(application) {
    private val database: GTFSDatabase = GTFSDatabase.getDatabase(application)
    private val repository: GTFSRepository = GTFSRepository(database)

    fun loadGTFSData() {
        viewModelScope.launch {
            try {
                repository.loadGTFSData(getApplication())
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun searchStops(query: String): Flow<List<StopEntity>> {
        return repository.searchStops(query)
    }

    fun getRoutesForStop(stopId: String): Flow<List<RouteEntity>> {
        return repository.getRoutesForStop(stopId)
    }

    fun getTimesForStop(stopId: String): Flow<List<StopTimeEntity>> {
        return repository.getTimesForStop(stopId)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GTFSViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GTFSViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
