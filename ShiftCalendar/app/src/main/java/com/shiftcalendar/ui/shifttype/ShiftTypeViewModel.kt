package com.shiftcalendar.ui.shifttype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.data.repository.ShiftTypeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShiftTypeViewModel(
    private val repository: ShiftTypeRepository
) : ViewModel() {

    private val _shiftTypes = MutableStateFlow<List<ShiftType>>(emptyList())
    val shiftTypes: StateFlow<List<ShiftType>> = _shiftTypes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadShiftTypes()
    }

    private fun loadShiftTypes() {
        viewModelScope.launch {
            repository.getAll().collect { types ->
                _shiftTypes.value = types
            }
        }
    }

    fun saveShiftType(shiftType: ShiftType) {
        viewModelScope.launch {
            if (shiftType.id == 0L) {
                repository.insert(shiftType)
            } else {
                repository.update(shiftType)
            }
        }
    }

    fun deleteShiftType(shiftType: ShiftType) {
        viewModelScope.launch {
            repository.delete(shiftType)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = ShiftCalendarApp.instance.database
            return ShiftTypeViewModel(ShiftTypeRepository(db.shiftTypeDao())) as T
        }
    }
}