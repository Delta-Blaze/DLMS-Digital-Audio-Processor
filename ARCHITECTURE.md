# Architecture

- `MainActivity.kt`: Compose shell, navigation, permissions and file picker.
- `AudioProcessorViewModel.kt`: single source of UI/audio state and user actions.
- `audio/AudioEngine.kt`: AudioRecord/MediaExtractor/MediaCodec/AudioTrack ownership and lifecycle.
- `effects/DspEffects.kt`: real float-buffer DSP effects and a single effect rack.
- `presets/PresetSystem.kt`: 80 built-in names plus user preset JSON persistence using DataStore Preferences.
- `state/AudioState.kt`: immutable UI state and central state store.

DSP is processed off the main thread. Parameter writes are state-owned by the ViewModel and applied to the rack, preventing the UI from touching raw audio objects.
