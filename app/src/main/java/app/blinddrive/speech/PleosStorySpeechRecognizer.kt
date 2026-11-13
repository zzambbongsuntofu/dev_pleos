package app.blinddrive.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/**
 * Pleos SpeechToText returns text only.
 * ON_DEVICE does not use Playground client id or secret.
 * Raw audio is not stored and sendAudio() is not called.
 */
class PleosStorySpeechRecognizer(
    context: Context,
) : StorySpeechRecognizer {
    private val main = Handler(Looper.getMainLooper())
    private val engine = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var textCallback: ((String, Boolean) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    @Volatile
    override var isReady: Boolean = false
        private set

    @Volatile
    override var backendLabel: String = "Pleos 음성 인식 확인 중"
        private set

    private fun publishLabel(value: String) {
        backendLabel = value
        val callback = onLabel ?: return
        main.post { callback(value) }
    }

    private var onLabel: ((String) -> Unit)? = null

    override fun prepare(onLabel: (String) -> Unit) {
        this.onLabel = onLabel
        publishLabel("Pleos 음성 인식 확인 중")
        try {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    engine.setLocale(Locale.KOREA)
                    engine.addListener(results)
                    isReady = true
                    publishLabel("Pleos 음성 인식 · 기기 내 모드")
                }

                override fun onFailure(errorType: ErrorType) {
                    isReady = false
                    publishLabel("Pleos 음성 인식 사용 불가 · ${speechErrorMessage(errorType.name)}")
                }
            })
        } catch (error: Throwable) {
            Log.w("BlindDrive", "Pleos SpeechToText did not start", error)
            isReady = false
            publishLabel("Pleos 음성 인식 사용 불가")
        }
    }

    override fun start(
        onText: (text: String, completed: Boolean) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        if (!isReady) {
            onError("Pleos 음성 인식을 사용할 수 없습니다")
            return
        }
        textCallback = onText
        errorCallback = onError
        try {
            engine.request()
        } catch (_: Throwable) {
            onError("음성 인식을 시작하지 못했습니다")
        }
    }

    override fun stop() {
        textCallback = null
        errorCallback = null
        try {
            engine.stop()
        } catch (_: Throwable) {
        }
    }

    override fun release() {
        stop()
        try {
            engine.removeListener(results)
            engine.release()
        } catch (_: Throwable) {
        }
        isReady = false
    }

    private val results = object : ResultListener {
        override fun onUpdated(stt: String, completed: Boolean) {
            val callback = textCallback ?: return
            main.post { callback(stt, completed) }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit

        override fun onStartedRecognition() = Unit

        override fun onEndedRecognition() = Unit

        override fun onError(errorType: ErrorType) {
            postError(errorType)
        }

        override fun onReady() = Unit

        override fun onUpdatedRms(rms: Double) = Unit
    }

    private fun postError(errorType: ErrorType) {
        val callback = errorCallback ?: return
        val message = speechErrorMessage(errorType.name)
        main.post { callback(message) }
    }
}

internal fun speechErrorMessage(name: String): String = when (name) {
    "NETWORK_ERROR" -> "네트워크 오류"
    "PERMISSION_DENIED" -> "권한이 거부되었습니다"
    "UNAUTHENTICATED" -> "SDK 인증에 실패했습니다"
    "NOT_SUPPORTED_API" -> "이 기기에서 지원하지 않는 API입니다"
    "MIC_ERROR" -> "마이크를 사용할 수 없습니다"
    "ON_DEVICE_ENGINE_ERROR" -> "기기 내 음성 엔진 오류"
    "TIMEOUT" -> "시간이 초과되었습니다"
    "UNAVAILABLE_LANGUAGE" -> "한국어를 사용할 수 없습니다"
    "AUDIO_ERROR" -> "오디오 오류"
    "SERVER_INTERNAL_ERROR" -> "음성 서버 오류"
    "INVALID_REQUEST_ERROR" -> "음성 요청이 거절되었습니다"
    else -> "음성 처리 오류"
}
