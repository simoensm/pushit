package com.pushitup.core.onboarding

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    /**
     * Null while DataStore hasn't emitted yet (avoids flash for returning users).
     * false = show tour. true = tour already done.
     */
    val isComplete: StateFlow<Boolean?> = ctx.onboardingStore
        .tourCompleteFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val _stepIndex = MutableStateFlow(0)
    val stepIndex: StateFlow<Int> = _stepIndex

    init {
        // If the tour is reset externally (e.g. from Settings), restart from step 0
        viewModelScope.launch {
            var previous: Boolean? = null
            ctx.onboardingStore.tourCompleteFlow().collect { complete ->
                if (previous == true && !complete) _stepIndex.value = 0
                previous = complete
            }
        }
    }

    val currentStep: TourStep?
        get() = ALL_TOUR_STEPS.getOrNull(_stepIndex.value)

    fun advance() {
        val next = _stepIndex.value + 1
        if (next >= ALL_TOUR_STEPS.size) {
            markComplete()
        } else {
            _stepIndex.update { next }
        }
    }

    fun skip() = markComplete()

    private fun markComplete() {
        viewModelScope.launch {
            ctx.onboardingStore.edit { it[OnboardingKeys.TOUR_COMPLETE] = true }
        }
    }
}
