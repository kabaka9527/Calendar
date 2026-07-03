package com.shiftcalendar.ui.calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.alarm.AlarmScheduler
import com.shiftcalendar.data.entity.ShiftDay
import com.shiftcalendar.data.entity.ShiftType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarViewModel : ViewModel() {

    private val db = ShiftCalendarApp.instance.database
    private val shiftDayDao = db.shiftDayDao()
    private val shiftTypeDao = db.shiftTypeDao()

    private val _currentMonthStart = MutableLiveData(getMonthStart())
    val currentMonthStart: LiveData<Long> = _currentMonthStart

    private val _isWeekView = MutableLiveData(false)
    val isWeekView: LiveData<Boolean> = _isWeekView

    private val _shiftDays = MutableLiveData<Map<Long, ShiftDay>>(emptyMap())
    val shiftDays: LiveData<Map<Long, ShiftDay>> = _shiftDays
    private val _shiftTypes = MutableLiveData<Map<Long, ShiftType>>(emptyMap())
    val shiftTypes: LiveData<Map<Long, ShiftType>> = _shiftTypes

    private val _selectedDay = MutableLiveData<ShiftDayDetail?>()
    val selectedDay: LiveData<ShiftDayDetail?> = _selectedDay

    init {
        loadShiftTypes()
        loadShiftDays()
    }

    private fun loadShiftTypes() {
        shiftTypeDao.getAllLiveData().observeForever { types ->
            _shiftTypes.postValue(types.associateBy { it.id })
        }
    }

    private fun loadShiftDays() {
        viewModelScope.launch {
            val range = getMonthRange()
            shiftDayDao.getByDateRangeLiveData(range.first, range.second)
                .observeForever { days ->
                    _shiftDays.postValue(days.associateBy { it.date })
                }
        }
    }

    /**
     * 重新加载当前视图范围的班次数据
     */
    private fun refreshShiftDays() {
        viewModelScope.launch {
            val range = getMonthRange()
            val days = shiftDayDao.getByDateRangeStatic(range.first, range.second)
            _shiftDays.postValue(days.associateBy { it.date })
        }
    }

    fun navigateToPrevious() {
        val cal = Calendar.getInstance().apply { timeInMillis = _currentMonthStart.value ?: getMonthStart() }
        if (_isWeekView.value == true) {
            cal.add(Calendar.WEEK_OF_YEAR, -1)
        } else {
            cal.add(Calendar.MONTH, -1)
        }
        _currentMonthStart.value = cal.timeInMillis
    }

    fun navigateToNext() {
        val cal = Calendar.getInstance().apply { timeInMillis = _currentMonthStart.value ?: getMonthStart() }
        if (_isWeekView.value == true) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        } else {
            cal.add(Calendar.MONTH, 1)
        }
        _currentMonthStart.value = cal.timeInMillis
    }

    fun toggleView() {
        _isWeekView.value = !(_isWeekView.value ?: false)
    }

    fun goToToday() {
        _currentMonthStart.value = getMonthStart()
    }

    fun selectDay(date: Long) {
        val shiftDay = _shiftDays.value?.get(date)
        val shiftType = shiftDay?.let { _shiftTypes.value?.get(it.shiftTypeId) }
        _selectedDay.value = ShiftDayDetail(
            date = date,
            shiftType = shiftType,
            note = shiftDay?.note ?: "",
            shiftDay = shiftDay
        )
    }

    fun clearSelection() {
        _selectedDay.value = null
    }

    /**
     * 保存备注到指定日期
     */
    fun saveNote(date: Long, note: String) {
        viewModelScope.launch {
            val existing = shiftDayDao.getByDate(date)
            if (existing != null) {
                shiftDayDao.updateNote(date, note)
            }
            refreshShiftDays()
        }
    }

    /**
     * 修改某天的班次类型（手动调整）
     */
    fun changeShiftType(date: Long, shiftTypeId: Long, note: String) {
        viewModelScope.launch {
            val existing = shiftDayDao.getByDate(date)
            if (existing != null) {
                shiftDayDao.updateShiftAndNote(date, shiftTypeId, note)
            } else {
                shiftDayDao.insertAll(listOf(
                    ShiftDay(date = date, shiftTypeId = shiftTypeId, isGenerated = false, note = note)
                ))
            }
            refreshShiftDays()
            scheduleNextSafely()
        }
    }

    private fun scheduleNextSafely() {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.scheduleNext(ShiftCalendarApp.instance)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun getMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = _currentMonthStart.value ?: getMonthStart()
            add(Calendar.MONTH, -3)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis

        cal.apply {
            timeInMillis = _currentMonthStart.value ?: getMonthStart()
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
    val note: String,
    val shiftDay: ShiftDay? = null
)