package app.blinddrive.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Device preview using Android's own speech synthesizer.
 * This is not Pleos TextToSpeech and does not play through a confirmed vehicle service.
 */
class LocalPreviewSpeaker(
    context: Context,
) : StorySpeaker {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val focus = SpeechAudioFocus(appContext)
    private var engine: TextToSpeech? = null
    private var onEvent: ((SpeakerEvent) -> Unit)? = null

    @Volatile
    override var isReady: Boolean = false
        private set

    override val backend: SpeakerBackend = SpeakerBackend.LOCAL_PREVIEW
    override val backendLabel: String = "이 기기 미리듣기 · Pleos 음성 합성 아님"

    override fun prepare(onLabel: (String) -> Unit) {
        onLabel(backendLabel)
        engine = TextToSpeech(appContext) { status ->
            val ready = status == TextToSpeech.SUCCESS
            isReady = ready
            if (ready) {
                engine?.language = Locale.KOREA
                engine?.setOnUtteranceProgressListener(progress)
            }
        }
    }

    override fun speak(text: String, onEvent: (SpeakerEvent) -> Unit) {
        this.onEvent = onEvent
        val current = engine
        if (!isReady || current == null) {
            post(SpeakerEvent.Failed("이 기기 미리듣기를 사용할 수 없습니다"))
            return
        }
        if (!focus.request { loseFocus() }) {
            post(SpeakerEvent.Failed("오디오 포커스를 얻지 못했습니다"))
            return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        current.setAudioAttributes(attrs)
        val result = current.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            focus.abandon()
            post(SpeakerEvent.Failed("미리듣기를 시작하지 못했습니다"))
        }
    }

    override fun stop() {
        engine?.stop()
        focus.abandon()
        post(SpeakerEvent.Stopped)
        onEvent = null
    }

    override fun release() {
        onEvent = null
        focus.abandon()
        engine?.stop()
        engine?.shutdown()
        engine = null
        isReady = false
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            post(SpeakerEvent.Started)
        }

        override fun onDone(utteranceId: String?) {
            focus.abandon()
            post(SpeakerEvent.Finished)
        }

        @Deprecated("Legacy callback")
        override fun onError(utteranceId: String?) {
            focus.abandon()
            post(SpeakerEvent.Failed("미리듣기 오류"))
        }
    }

    private fun loseFocus() {
        engine?.stop()
        post(SpeakerEvent.FocusLost)
    }

    private fun post(event: SpeakerEvent) {
        val callback = onEvent ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(event)
        } else {
            main.post { onEvent?.invoke(event) }
        }
    }

    private companion object {
        const val UTTERANCE_ID = "blind-drive-preview"
    }
}
