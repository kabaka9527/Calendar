package com.shiftcalendar.ui.shifttype

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftType
import com.shiftcalendar.data.repository.ShiftTypeRepository
import kotlinx.coroutines.launch

class ShiftTypeViewModel(
    private val repository: ShiftTypeRepository
) : ViewModel() {

    private val _shiftTypes = MutableLiveData<List<ShiftType>>(emptyList())
    val shiftTypes: LiveData<List<ShiftType>> = _shiftTypes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadShiftTypes()
    }

    private fun loadShiftTypes() {
        viewModelScope.launch {
            repository.getAll().collect { types ->
                _shiftTypes.postValue(types)
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