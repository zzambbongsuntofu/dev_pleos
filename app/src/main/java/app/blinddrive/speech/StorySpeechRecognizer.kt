package app.blinddrive.speech

interface StorySpeechRecognizer {
    val backendLabel: String
    val isReady: Boolean

    fun prepare(onLabel: (String) -> Unit)

    fun start(
        onText: (text: String, completed: Boolean) -> Unit,
        onError: (message: String) -> Unit,
    )

    fun stop()

    fun release()
}
