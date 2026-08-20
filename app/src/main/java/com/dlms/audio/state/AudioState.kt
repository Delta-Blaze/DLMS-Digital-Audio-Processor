package com.dlms.audio.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EffectState(val enabled: Boolean = true, val mix: Float = 1f, val params: Map<String, Float> = emptyMap())

data class AudioProcessorState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentFile: String? = null,
    val currentPreset: String = "Clean Vocal",
    val masterVolume: Float = 0.85f,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val peak: Float = 0f,
    val rms: Float = 0f,
    val sampleRate: Int = 48_000,
    val bufferSize: Int = 2048,
    val channels: Int = 2,
    val activeNodes: List<String> = emptyList(),
    val routingValid: Boolean = true,
    val underruns: Long = 0,
    val dspErrors: Long = 0,
    val engineStatus: String = "Stopped",
    val error: String? = null,
    val effectStates: Map<String, EffectState> = emptyMap()
)

class AudioStateStore {
    private val _state = MutableStateFlow(AudioProcessorState())
    val state: StateFlow<AudioProcessorState> = _state.asStateFlow()
    fun update(transform: (AudioProcessorState) -> AudioProcessorState) { _state.value = transform(_state.value) }
    fun clearError() { update { it.copy(error = null) } }
}
