package com.shiftcalendar.ui.shiftrule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shiftcalendar.ShiftCalendarApp
import com.shiftcalendar.data.entity.ShiftRule
import com.shiftcalendar.data.repository.ShiftRuleRepository
import com.shiftcalendar.domain.generator.ShiftGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShiftRuleViewModel(
    private val repository: ShiftRuleRepository
) : ViewModel() {

    private val _rules = MutableLiveData<List<ShiftRule>>(emptyList())
    val rules: LiveData<List<ShiftRule>> = _rules

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _generationProgress = MutableLiveData("")
    val generationProgress: LiveData<String> = _generationProgress

    private val generator = ShiftGenerator(ShiftCalendarApp.instance.database)

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            repository.getAll().collect { rules ->
                _rules.postValue(rules)
            }
        }
    }

    fun saveRule(rule: ShiftRule) {
        viewModelScope.launch {
            val id = if (rule.id == 0L) {
                repository.insert(rule)
            } else {
                repository.update(rule)
                rule.id
            }
        }
    }

    fun deleteRule(rule: ShiftRule) {
        viewModelScope.launch {
            repository.deactivate(rule.id)
        }
    }

    fun generateShifts(rule: ShiftRule, days: Int = 90) {
        viewModelScope.launch {
            _isLoading.value = true
            _generationProgress.value = "正在生成班次…"
            try {
                withContext(Dispatchers.IO) {
                    // 清除该规律日期之后的旧数据
                    generator.clearFromDate(rule.startDate)
                    // 生成新班次
                    generator.generate(rule, days)
                }
                _generationProgress.value = "班次生成完成"
            } catch (e: Exception) {
                _generationProgress.value = "生成失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = ShiftCalendarApp.instance.database
            return ShiftRuleViewModel(ShiftRuleRepository(db.shiftRuleDao())) as T
        }
    }
}