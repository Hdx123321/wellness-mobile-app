package com.wellnessmate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wellnessmate.app.data.HealthProfileRepository
import com.wellnessmate.app.data.ProfileResponse
import com.wellnessmate.app.data.TrackerRepository
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HealthMetrics(
    val age: Int,
    val currentWeightKg: Double,
    val bmi: Double,
    val basalMetabolismKcal: Int,
    val basalMetabolismText: String,
    val fatBurningHeartRateText: String,
    val goalProgress: Double?,
    val goalEndDate: LocalDate?,
)

data class HealthProfileUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val profile: ProfileResponse? = null,
    val metrics: HealthMetrics? = null,
    val error: String? = null,
)

class HealthProfileViewModel(
    private val profiles: HealthProfileRepository,
    private val trackers: TrackerRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HealthProfileUiState())
    val state: StateFlow<HealthProfileUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val profileResult = profiles.profile()
            val weightResult = trackers.entries(type = "WEIGHT")
            if (profileResult.isSuccess) {
                val profile = profileResult.getOrThrow()
                val latestWeight = weightResult.getOrNull()?.maxByOrNull { it.recordedAt }?.amount
                _state.value = HealthProfileUiState(
                    loading = false,
                    profile = profile,
                    metrics = calculateHealthMetrics(profile, latestWeight),
                    error = weightResult.exceptionOrNull()?.message,
                )
            } else {
                _state.value = _state.value.copy(loading = false, error = profileResult.exceptionOrNull()?.message)
            }
        }
    }

    fun updateHeight(heightCm: Double, onSaved: () -> Unit) {
        val profile = _state.value.profile ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            profiles.updateHeight(profile, heightCm).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        saving = false,
                        profile = it,
                        metrics = calculateHealthMetrics(it, _state.value.metrics?.currentWeightKg),
                    )
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun updateGoal(targetWeightKg: Double?, goalDurationWeeks: Int?, onSaved: () -> Unit) {
        val profile = _state.value.profile ?: return
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            profiles.updateGoal(profile, targetWeightKg, goalDurationWeeks).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        saving = false,
                        profile = it,
                        metrics = calculateHealthMetrics(it, _state.value.metrics?.currentWeightKg),
                    )
                    onSaved()
                },
                onFailure = { _state.value = _state.value.copy(saving = false, error = it.message) },
            )
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    companion object {
        fun factory(
            profiles: HealthProfileRepository,
            trackers: TrackerRepository,
        ): ViewModelProvider.Factory = factoryOf { HealthProfileViewModel(profiles, trackers) }
    }
}

fun calculateHealthMetrics(profile: ProfileResponse, latestWeightKg: Double?): HealthMetrics {
    val today = LocalDate.now()
    val age = Period.between(LocalDate.parse(profile.dateOfBirth), today).years.coerceAtLeast(0)
    val weight = latestWeightKg ?: profile.currentWeightKg
    val bmi = weight / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))
    val commonBmr = 10 * weight + 6.25 * profile.heightCm - 5 * age
    val femaleBmr = (commonBmr - 161).roundToInt()
    val maleBmr = (commonBmr + 5).roundToInt()
    val bmr = when (profile.sex) {
        "MALE" -> "$maleBmr kcal/day"
        "FEMALE" -> "$femaleBmr kcal/day"
        else -> "$femaleBmr–$maleBmr kcal/day"
    }
    val bmrKcal = when (profile.sex) {
        "MALE" -> maleBmr
        "FEMALE" -> femaleBmr
        else -> ((femaleBmr + maleBmr) / 2.0).roundToInt()
    }
    val maxHeartRate = (220 - age).coerceAtLeast(1)
    val heartRate = "${(maxHeartRate * 0.5).roundToInt()}–${(maxHeartRate * 0.7).roundToInt()} bpm"
    val target = profile.targetWeightKg
    val progress = if (target != null && profile.currentWeightKg != target) {
        ((profile.currentWeightKg - weight) / (profile.currentWeightKg - target)).coerceIn(0.0, 1.0)
    } else null
    val endDate = profile.goalStartedAt?.let(LocalDate::parse)
        ?.plusWeeks(profile.goalDurationWeeks?.toLong() ?: 0)
    return HealthMetrics(age, weight, bmi, bmrKcal, bmr, heartRate, progress, endDate)
}
