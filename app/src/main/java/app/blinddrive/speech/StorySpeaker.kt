package app.blinddrive.speech

enum class SpeakerBackend {
    PLEOS_ON_DEVICE,
    LOCAL_PREVIEW,
    UNAVAILABLE,
}

sealed interface SpeakerEvent {
    data object Started : SpeakerEvent
    data object Finished : SpeakerEvent
    data object Stopped : SpeakerEvent
    data object FocusLost : SpeakerEvent
    data class Failed(val message: String) : SpeakerEvent
}

interface StorySpeaker {
    val backend: SpeakerBackend
    val backendLabel: String
    val isReady: Boolean

    fun prepare(onLabel: (String) -> Unit)

    fun speak(text: String, onEvent: (SpeakerEvent) -> Unit)

    fun stop()

    fun release()
}
