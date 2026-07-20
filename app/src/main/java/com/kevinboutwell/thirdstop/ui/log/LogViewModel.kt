package com.kevinboutwell.thirdstop.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinboutwell.thirdstop.LightMeterApp
import com.kevinboutwell.thirdstop.data.ReadingDao
import com.kevinboutwell.thirdstop.data.ReadingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogViewModel(
    private val dao: ReadingDao,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val readings: StateFlow<List<ReadingEntity>> =
        dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastDeleted: ReadingEntity? = null

    // appScope: a delete/undo must complete even if the user backs out of the
    // screen before the write lands.
    fun delete(reading: ReadingEntity) {
        lastDeleted = reading
        appScope.launch { dao.delete(reading) }
    }

    fun undoDelete() {
        val reading = lastDeleted ?: return
        lastDeleted = null
        appScope.launch { dao.insert(reading.copy(id = 0)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LightMeterApp
                LogViewModel(app.container.readingDao, app.container.applicationScope)
            }
        }
    }
}
