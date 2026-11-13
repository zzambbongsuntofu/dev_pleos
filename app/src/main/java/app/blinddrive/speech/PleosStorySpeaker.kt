package app.blinddrive.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/**
 * Speaks curated text through Pleos TextToSpeech.
 * This is not playback of an original recording.
 * ON_DEVICE does not use Playground client id or secret.
 */
class PleosStorySpeaker(
    context: Context,
) : StorySpeaker {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val focus = SpeechAudioFocus(appContext)
    private val engine = TextToSpeech(appContext, Mode.ON_DEVICE)
    private var onEvent: ((SpeakerEvent) -> Unit)? = null

    @Volatile
    override var isReady: Boolean = false
        private set

    override val backend: SpeakerBackend = SpeakerBackend.PLEOS_ON_DEVICE

    @Volatile
    override var backendLabel: String = "Pleos 음성 합성 확인 중"
        private set

    private fun publishLabel(value: String) {
        backendLabel = value
        val callback = onLabel ?: return
        main.post { callback(value) }
    }

    private var onLabel: ((String) -> Unit)? = null

    override fun prepare(onLabel: (String) -> Unit) {
        this.onLabel = onLabel
        publishLabel("Pleos 음성 합성 확인 중")
        try {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        // The engine rejects settings until it has connected.
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        )
                        engine.setLocale(Locale.KOREA)
                        engine.addEventListener(events)
                        isReady = true
                        publishLabel("Pleos 음성 합성 · 기기 내 모드")
                    } catch (error: Throwable) {
                        Log.w("BlindDrive", "Pleos TextToSpeech setup failed", error)
                        isReady = false
                        publishLabel("Pleos 음성 합성 사용 불가")
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    isReady = false
                    publishLabel("Pleos 음성 합성 사용 불가 · ${speechErrorMessage(errorType.name)}")
                }
            })
        } catch (error: Throwable) {
            Log.w("BlindDrive", "Pleos TextToSpeech did not start", error)
            isReady = false
            publishLabel("Pleos 음성 합성 사용 불가")
        }
    }

    override fun speak(text: String, onEvent: (SpeakerEvent) -> Unit) {
        this.onEvent = onEvent
        if (!isReady) {
            post(SpeakerEvent.Failed("Pleos 음성 합성을 사용할 수 없습니다"))
            return
        }
        if (!focus.request { loseFocus() }) {
            post(SpeakerEvent.Failed("오디오 포커스를 얻지 못했습니다"))
            return
        }
        try {
            engine.speak(text, true)
        } catch (_: Throwable) {
            focus.abandon()
            post(SpeakerEvent.Failed("음성 합성을 시작하지 못했습니다"))
        }
    }

    override fun stop() {
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
        focus.abandon()
        post(SpeakerEvent.Stopped)
        onEvent = null
    }

    override fun release() {
        onEvent = null
        focus.abandon()
        try {
            engine.removeEventListener(events)
            engine.release()
        } catch (_: Throwable) {
        }
        isReady = false
    }

    private val events = object : EventListener {
        override fun onStart() {
            post(SpeakerEvent.Started)
        }

        override fun onReady() = Unit

        override fun onDone() {
            focus.abandon()
            post(SpeakerEvent.Finished)
        }

        override fun onError(errorType: ErrorType) {
            focus.abandon()
            post(SpeakerEvent.Failed(speechErrorMessage(errorType.name)))
        }

        override fun onStop() {
            focus.abandon()
            post(SpeakerEvent.Stopped)
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun loseFocus() {
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
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
}
