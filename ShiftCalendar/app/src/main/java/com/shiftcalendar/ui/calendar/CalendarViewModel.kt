package com.shiftcalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarViewModel : ViewModel() {

    private val db = ShiftCalendarApp.instance.database
    private val shiftDayDao = db.shiftDayDao()
    private val shiftTypeDao = db.shiftTypeDao()

    private val _currentMonthStart = MutableStateFlow(getMonthStart())
    val currentMonthStart: StateFlow<Long> = _currentMonthStart.asStateFlow()

    private val _isWeekView = MutableStateFlow(false)
    val isWeekView: StateFlow<Boolean> = _isWeekView.asStateFlow()

    private val _shiftDays = MutableStateFlow<Map<Long, ShiftDay>>(emptyMap())
    private val _shiftTypes = MutableStateFlow<Map<Long, ShiftType>>(emptyMap())

    val calendarData: StateFlow<CalendarData> = combine(
        _shiftDays, _shiftTypes, _currentMonthStart, _isWeekView
    ) { days, types, monthStart, isWeek ->
        CalendarData(days, types, monthStart, isWeek)
    }.asStateFlow()

    private val _selectedDay = MutableStateFlow<ShiftDayDetail?>(null)
    val selectedDay: StateFlow<ShiftDayDetail?> = _selectedDay.asStateFlow()

    init {
        loadShiftTypes()
        loadShiftDays()
    }

    private fun loadShiftTypes() {
        viewModelScope.launch {
            shiftTypeDao.getAllLiveData().observeForever { types ->
                _shiftTypes.value = types.associateBy { it.id }
            }
        }
    }

    private fun loadShiftDays() {
        viewModelScope.launch {
            // 加载当前月份前后各 3 个月的数据
            val range = getMonthRange()
            shiftDayDao.getByDateRangeLiveData(range.first, range.second)
                .observeForever { days ->
                    _shiftDays.value = days.associateBy { it.date }
                }
        }
    }

    fun navigateToPrevious() {
        val cal = Calendar.getInstance().apply { timeInMillis = _currentMonthStart.value }
        if (_isWeekView.value) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        } else {
            cal.add(Calendar.MONTH, -1)
        }
        _currentMonthStart.value = cal.timeInMillis
    }

    fun navigateToNext() {
        val cal = Calendar.getInstance().apply { timeInMillis = _currentMonthStart.value }
        if (_isWeekView.value) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        } else {
            cal.add(Calendar.MONTH, 1)
        }
        _currentMonthStart.value = cal.timeInMillis
    }

    fun toggleView() {
        _isWeekView.value = !_isWeekView.value
    }

    fun goToToday() {
        _currentMonthStart.value = getMonthStart()
    }

    fun selectDay(date: Long) {
        val shiftDay = _shiftDays.value[date]
        val shiftType = shiftDay?.let { _shiftTypes.value[it.shiftTypeId] }
        _selectedDay.value = ShiftDayDetail(
            date = date,
            shiftType = shiftType,
            note = shiftDay?.note ?: ""
        )
    }

    fun clearSelection() {
        _selectedDay.value = null
    }

    private fun getMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _currentMonthStart.value
            add(Calendar.MONTH, -3)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        cal.apply {
            timeInMillis = _currentMonthStart.value
            add(Calendar.MONTH, 4)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel() as T
        }
    }

    private fun getMonthStart(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

data class CalendarData(
    val shiftDays: Map<Long, ShiftDay>,
    val shiftTypes: Map<Long, ShiftType>,
    val monthStart: Long,
    val isWeekView: Boolean
)

data class ShiftDayDetail(
    val date: Long,
    val shiftType: ShiftType?,
    val note: String
)