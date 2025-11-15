package com.standandsip.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CountdownViewModel : ViewModel() {
    private val _secondsLeft = MutableStateFlow(60)
    val secondsLeft: StateFlow<Int> = _secondsLeft

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    fun startCountdown(durationSec: Int = 10, onFinished: () -> Unit) {  // default 10s
        if (_isRunning.value) return
        _isRunning.value = true
        viewModelScope.launch {
            for (i in durationSec downTo 0) {
                _secondsLeft.value = i
                delay(1000L)
            }
            _isRunning.value = false
            onFinished()
        }
    }

    fun resetTo(durationSec: Int = 10) {
        _secondsLeft.value = durationSec
        _isRunning.value = false
    }
}