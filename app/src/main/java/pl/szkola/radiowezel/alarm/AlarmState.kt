package pl.szkola.radiowezel.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AlarmState {
    private val _active = MutableStateFlow<AlarmType?>(null)
    val active: StateFlow<AlarmType?> = _active

    val isActive: Boolean get() = _active.value != null

    fun setActive(type: AlarmType?) {
        _active.value = type
    }
}
