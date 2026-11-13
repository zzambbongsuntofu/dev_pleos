package app.blinddrive.speech

import android.os.Handler
import android.os.Looper

/** Canned text. This does not record a microphone and is not Pleos SpeechToText. */
class MockStorySpeechRecognizer : StorySpeechRecognizer {
    private val main = Handler(Looper.getMainLooper())
    private var running = false

    override val backendLabel: String = "로컬 모의 인식 · Pleos 음성 인식 아님"
    override val isReady: Boolean = true

    override fun prepare(onLabel: (String) -> Unit) {
        onLabel(backendLabel)
    }

    override fun start(
        onText: (text: String, completed: Boolean) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        running = true
        main.postDelayed({
            if (!running) return@postDelayed
            onText("오늘 노을이 길어서 집으로 천천히 갔습니다.", true)
            running = false
        }, 700)
    }

    override fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
    }

    override fun release() {
        stop()
    }
}
