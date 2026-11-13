package app.blinddrive.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.blinddrive.model.CabinOrigin
import app.blinddrive.model.CabinSnapshot
import app.blinddrive.model.Motion
import app.blinddrive.policy.CabinPolicy
import app.blinddrive.speech.LocalPreviewSpeaker
import app.blinddrive.speech.MockStorySpeechRecognizer
import app.blinddrive.speech.PleosStorySpeaker
import app.blinddrive.speech.PleosStorySpeechRecognizer
import app.blinddrive.speech.SpeakerEvent
import app.blinddrive.speech.StorySpeaker
import app.blinddrive.story.LocalSubmission
import app.blinddrive.story.SampleStories
import app.blinddrive.vehicle.AutomotiveCabinStatusSource
import app.blinddrive.vehicle.BEGINNER_IDLE_LINE
import app.blinddrive.vehicle.BeginnerAdvice
import app.blinddrive.vehicle.BeginnerLamp
import app.blinddrive.vehicle.BeginnerSignals
import app.blinddrive.vehicle.MockCabinStatusSource
import app.blinddrive.vehicle.MockMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class InputPhase {
    Idle,
    Consent,
    Listening,
    Confirm,
}

enum class VoteChoice {
    A,
    B,
}

data class UiState(
    val cabin: CabinSnapshot = CabinSnapshot(
        origin = CabinOrigin.VEHICLE_UNAVAILABLE,
        motion = Motion.UNKNOWN,
        activeRestrictions = 0,
        detail = "차량 신호를 기다리는 중",
    ),
    val mockSelected: Boolean = false,
    val networkAvailable: Boolean? = null,
    val recognizerLabel: String = "음성 인식 확인 중",
    val speakerLabel: String = "음성 합성 확인 중",
    val notice: String = "",
    val phase: InputPhase = InputPhase.Idle,
    val draft: String = "",
    val storyIndex: Int = 0,
    val playing: Boolean = false,
    val voiceLine: String = "주차한 뒤에 연습할 수 있어요",
    val lastAdvice: String? = null,
    val lamps: Set<BeginnerLamp> = emptySet(),
    val steerAngle: Float? = null,
    /** True while the debug walk-through plays. Lines and lamps are then not vehicle signals. */
    val demo: Boolean = false,
    /** True while an advice line should cover the whole screen. */
    val alert: Boolean = false,
    val votes: Map<String, VoteChoice> = emptyMap(),
    val latestSubmission: LocalSubmission? = null,
    val submissionCount: Int = 0,
)

class BlindDriveViewModel(application: Application) : AndroidViewModel(application) {
    private val stories = SampleStories.items
    private val mocks = MockCabinStatusSource()
    private val vehicleSource = AutomotiveCabinStatusSource(application)
    private val vehicleSnapshots = MutableStateFlow(
        CabinSnapshot(
            origin = CabinOrigin.VEHICLE_UNAVAILABLE,
            motion = Motion.UNKNOWN,
            activeRestrictions = 0,
            detail = "차량 신호를 기다리는 중",
        ),
    )
    private val mockMode = MutableStateFlow<MockMotion?>(null)
    private val mockRecognizer = MockStorySpeechRecognizer()
    private val pleosRecognizer: PleosStorySpeechRecognizer? = try {
        PleosStorySpeechRecognizer(application)
    } catch (_: Throwable) {
        null
    }
    private val pleosSpeaker: PleosStorySpeaker? = try {
        PleosStorySpeaker(application)
    } catch (_: Throwable) {
        null
    }
    private val previewSpeaker = LocalPreviewSpeaker(application)
    private var activeSpeaker: StorySpeaker? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val beginnerSignals = BeginnerSignals(
        application,
        ::onVehicleNews,
        ::onVehicleLamps,
        ::onVehicleSteer,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** The walk-through exists only in debuggable builds. */
    val demoAvailable: Boolean =
        (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private var demoJob: Job? = null
    private var alertJob: Job? = null
    private var vehicleLamps: Set<BeginnerLamp> = emptySet()

    init {
        combine(vehicleSnapshots, mockMode) { vehicle, mock ->
            if (mock == null) vehicle else mocks.snapshot(mock)
        }.onEach { snapshot ->
            _ui.update { it.copy(cabin = snapshot, mockSelected = snapshot.origin == CabinOrigin.LOCAL_MOCK) }
            enforce(snapshot)
        }.launchIn(viewModelScope)

        vehicleSource.start { vehicleSnapshots.value = it }
        pleosRecognizer?.prepare { label ->
            Log.i(TAG, "stt: $label")
            _ui.update { it.copy(recognizerLabel = label) }
        } ?: _ui.update { it.copy(recognizerLabel = "Pleos 음성 인식 사용 불가") }
        mockRecognizer.prepare { }
        pleosSpeaker?.prepare { label ->
            Log.i(TAG, "tts: $label")
            _ui.update { it.copy(speakerLabel = label) }
        } ?: _ui.update { it.copy(speakerLabel = "Pleos 음성 합성 사용 불가") }
        previewSpeaker.prepare { }
        beginnerSignals.start()
        watchNetwork()
    }

    fun useVehicleSignal() {
        mockMode.value = null
    }

    fun useMock(mode: MockMotion) {
        mockMode.value = mode
    }

    fun onHostVisible() = Unit

    fun onHostHidden() {
        stopRecognition()
        stopDemo()
        _ui.update { state ->
            if (state.phase == InputPhase.Listening) state.copy(phase = InputPhase.Idle) else state
        }
    }

    /**
     * Dev button. Runs Pleos TextToSpeech only; it never falls back to the Android preview voice,
     * so what is heard is always the Pleos voice. The parked-only gate still applies.
     */
    /** Repeats the last advice, or the status line if nothing was said yet. It does not read a route. */
    fun togglePractice() {
        if (_ui.value.playing) {
            stopPlayback()
            return
        }
        val line = _ui.value.lastAdvice ?: _ui.value.voiceLine
        speakPleos(line, line)
    }

    /**
     * Debug builds only. Shows and speaks every advice line in order so the screen can be
     * judged without a vehicle. Nothing here comes from the car, and the screen says so.
     * Vehicle advice that arrives meanwhile is not shown.
     */
    fun toggleDemo() {
        if (demoJob != null) {
            stopDemo()
            return
        }
        if (!demoAvailable) return
        val gate = CabinPolicy.listen(_ui.value.cabin)
        if (!gate.allowed) {
            _ui.update { it.copy(voiceLine = gate.reason) }
            return
        }
        stopPlayback()
        val adviceBefore = _ui.value.lastAdvice
        demoJob = viewModelScope.launch {
            try {
                _ui.update { it.copy(demo = true) }
                for (advice in BeginnerAdvice.entries) {
                    _ui.update {
                        it.copy(
                            voiceLine = advice.line,
                            lastAdvice = advice.line,
                            lamps = setOfNotNull(advice.lamp),
                            alert = true,
                        )
                    }
                    speakPleos(advice.line, advice.line)
                    delay(DEMO_SPEECH_START_MS)
                    withTimeoutOrNull(DEMO_SPEECH_LIMIT_MS) { ui.first { !it.playing } }
                    delay(DEMO_PAUSE_MS)
                }
            } finally {
                demoJob = null
                _ui.update {
                    it.copy(
                        demo = false,
                        alert = false,
                        voiceLine = BEGINNER_IDLE_LINE,
                        lastAdvice = adviceBefore,
                        lamps = vehicleLamps,
                    )
                }
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        stopPlayback()
    }

    private fun onVehicleLamps(lamps: Set<BeginnerLamp>) {
        vehicleLamps = lamps
        if (_ui.value.demo) return
        _ui.update { it.copy(lamps = lamps, alert = it.alert || lamps.isNotEmpty()) }
        if (lamps.isEmpty() && !_ui.value.playing) armAlertEnd() else alertJob?.cancel()
    }

    private fun onVehicleSteer(angle: Float) {
        _ui.update { it.copy(steerAngle = angle) }
    }

    /** The driver allowed the speed reading on the system prompt. */
    fun onSpeedAccessGranted() {
        beginnerSignals.retrySpeed()
    }

    private fun onVehicleNews(line: String, speak: Boolean) {
        if (_ui.value.demo) return
        if (speak) alertJob?.cancel()
        _ui.update {
            it.copy(
                voiceLine = line,
                lastAdvice = if (speak) line else it.lastAdvice,
                alert = if (speak) true else it.alert,
            )
        }
        if (!speak || _ui.value.playing) return
        if (!CabinPolicy.listen(_ui.value.cabin).allowed) return
        speakPleos(line, line)
    }

    private fun speakPleos(text: String, startedLine: String) {
        val gate = CabinPolicy.listen(_ui.value.cabin)
        if (!gate.allowed) {
            _ui.update { it.copy(voiceLine = gate.reason, alert = false) }
            return
        }
        val speaker = pleosSpeaker
        if (speaker == null || !speaker.isReady) {
            _ui.update { it.copy(voiceLine = speaker?.backendLabel ?: "Pleos 음성 합성 사용 불가", alert = false) }
            return
        }
        activeSpeaker = speaker
        _ui.update { it.copy(voiceLine = startedLine, alert = isAdvice(startedLine) || it.alert) }
        speaker.speak(text) { event ->
            Log.i(TAG, "tts event: $event")
            onSpeakerEvent(event)
            if (event is SpeakerEvent.Failed) {
                _ui.update { it.copy(voiceLine = "실패: ${event.message}", alert = false) }
            } else if (event == SpeakerEvent.Finished && !_ui.value.demo && _ui.value.lamps.isEmpty()) {
                armAlertEnd()
            }
        }
    }

    /** Keeps the line readable for a moment after the voice, then returns to the passenger. */
    private fun armAlertEnd() {
        alertJob?.cancel()
        alertJob = viewModelScope.launch {
            delay(ALERT_HOLD_MS)
            val state = _ui.value
            if (!state.demo && !state.playing && state.lamps.isEmpty()) {
                _ui.update { it.copy(alert = false) }
            }
        }
    }

    private fun isAdvice(line: String): Boolean = BeginnerAdvice.entries.any { it.line == line }

    fun toggleDevVoice() {
        if (_ui.value.playing) {
            stopPlayback()
            _ui.update { it.copy(voiceLine = "멈춤") }
            return
        }
        val gate = CabinPolicy.listen(_ui.value.cabin)
        if (!gate.allowed) {
            _ui.update { it.copy(voiceLine = "차량 상태: ${gate.reason}") }
            return
        }
        val speaker = pleosSpeaker
        if (speaker == null || !speaker.isReady) {
            _ui.update { it.copy(voiceLine = speaker?.backendLabel ?: "Pleos 음성 합성 사용 불가") }
            return
        }
        activeSpeaker = speaker
        _ui.update { it.copy(voiceLine = "Pleos 음성 요청함") }
        speaker.speak(stories[0].text) { event ->
            Log.i(TAG, "tts event: $event")
            onSpeakerEvent(event)
            _ui.update {
                it.copy(
                    voiceLine = when (event) {
                        SpeakerEvent.Started -> "Pleos 음성 재생 중"
                        SpeakerEvent.Finished -> "재생 끝"
                        SpeakerEvent.Stopped -> "멈춤"
                        SpeakerEvent.FocusLost -> "오디오 포커스를 잃었습니다"
                        is SpeakerEvent.Failed -> "실패: ${event.message}"
                    },
                )
            }
        }
    }

    fun togglePlayback() {
        val snapshot = _ui.value.cabin
        if (!CabinPolicy.listen(snapshot).allowed) return
        if (_ui.value.playing) {
            stopPlayback()
            return
        }
        val speaker = if (pleosSpeaker?.isReady == true) pleosSpeaker else previewSpeaker
        activeSpeaker = speaker
        val text = stories[_ui.value.storyIndex].text
        _ui.update {
            it.copy(
                speakerLabel = speaker.backendLabel,
                notice = if (speaker === pleosSpeaker) {
                    "샘플 글을 읽고 있습니다"
                } else {
                    "샘플 글을 이 기기에서만 미리 듣습니다"
                },
            )
        }
        speaker.speak(text) { event -> onSpeakerEvent(event) }
    }

    fun showPrevious() = moveStory(-1)

    fun showNext() = moveStory(1)

    fun vote(choice: VoteChoice) {
        if (!CabinPolicy.vote(_ui.value.cabin).allowed) return
        val id = stories[_ui.value.storyIndex].id
        _ui.update { state ->
            state.copy(
                votes = state.votes + (id to choice),
                notice = "이 기기에만 기록했습니다. 서버 집계는 없습니다",
            )
        }
    }

    fun requestStorySpeech() {
        if (!CabinPolicy.storyInput(_ui.value.cabin).allowed) return
        _ui.update { it.copy(phase = InputPhase.Consent) }
    }

    fun grantConsent() {
        if (!CabinPolicy.storyInput(_ui.value.cabin).allowed) return
        val recognizer = if (pleosRecognizer?.isReady == true) pleosRecognizer else mockRecognizer
        _ui.update {
            it.copy(
                phase = InputPhase.Listening,
                draft = "",
                recognizerLabel = recognizer.backendLabel,
                notice = if (recognizer === pleosRecognizer) {
                    "듣고 있습니다. 글자로만 받습니다"
                } else {
                    "로컬 모의 인식입니다. 마이크를 쓰지 않습니다"
                },
            )
        }
        recognizer.start(
            onText = { text, completed ->
                val clipped = text.take(MAX_DRAFT)
                _ui.update { state ->
                    state.copy(
                        draft = clipped,
                        phase = if (completed) InputPhase.Confirm else InputPhase.Listening,
                        notice = if (text.length > MAX_DRAFT) {
                            "긴 문장은 ${MAX_DRAFT}자까지만 남겼습니다"
                        } else {
                            state.notice
                        },
                    )
                }
                if (completed) stopRecognition()
            },
            onError = { message ->
                stopRecognition()
                _ui.update { it.copy(phase = InputPhase.Idle, notice = message) }
            },
        )
    }

    fun dismissConsent() {
        _ui.update { it.copy(phase = InputPhase.Idle) }
    }

    fun stopStorySpeech() {
        stopRecognition()
        _ui.update { state ->
            if (state.draft.isBlank()) {
                state.copy(phase = InputPhase.Idle)
            } else {
                state.copy(phase = InputPhase.Confirm)
            }
        }
    }

    fun updateDraft(value: String) {
        if (!CabinPolicy.storyEdit(_ui.value.cabin).allowed) return
        _ui.update { it.copy(draft = value.take(MAX_DRAFT)) }
    }

    fun submitDraft() {
        if (!CabinPolicy.storySubmit(_ui.value.cabin).allowed) return
        val text = _ui.value.draft.trim()
        if (text.isEmpty()) return
        val submission = LocalSubmission(text = text)
        _ui.update { state ->
            state.copy(
                phase = InputPhase.Idle,
                draft = "",
                latestSubmission = submission,
                submissionCount = state.submissionCount + 1,
                notice = "이 기기에만 두었습니다. 익명화, 검토, 서버 전송은 하지 않았습니다",
            )
        }
    }

    override fun onCleared() {
        val connectivity = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { callback ->
            try {
                connectivity?.unregisterNetworkCallback(callback)
            } catch (_: Throwable) {
            }
        }
        stopRecognition()
        pleosRecognizer?.release()
        mockRecognizer.release()
        pleosSpeaker?.release()
        previewSpeaker.release()
        beginnerSignals.stop()
        vehicleSource.stop()
        super.onCleared()
    }

    private fun moveStory(delta: Int) {
        if (!CabinPolicy.listen(_ui.value.cabin).allowed) return
        stopPlayback()
        _ui.update { state ->
            val count = stories.size
            val next = (state.storyIndex + delta).floorMod(count)
            state.copy(storyIndex = next, playing = false)
        }
    }

    private fun enforce(snapshot: CabinSnapshot) {
        if (!CabinPolicy.listen(snapshot).allowed) {
            stopDemo()
        }
        if (!CabinPolicy.storyInput(snapshot).allowed) {
            stopRecognition()
            _ui.update { state ->
                if (state.phase == InputPhase.Listening || state.phase == InputPhase.Consent) {
                    state.copy(phase = InputPhase.Idle, notice = "입력을 멈췄습니다")
                } else {
                    state
                }
            }
        }
    }

    private fun onSpeakerEvent(event: SpeakerEvent) {
        when (event) {
            SpeakerEvent.Started -> _ui.update { it.copy(playing = true) }
            SpeakerEvent.Finished -> _ui.update { it.copy(playing = false, notice = "재생이 끝났습니다") }
            SpeakerEvent.Stopped -> _ui.update { it.copy(playing = false) }
            SpeakerEvent.FocusLost -> _ui.update {
                it.copy(playing = false, notice = "오디오 포커스를 잃었습니다")
            }
            is SpeakerEvent.Failed -> _ui.update { it.copy(playing = false, notice = event.message) }
        }
    }

    private fun stopRecognition() {
        pleosRecognizer?.stop()
        mockRecognizer.stop()
    }

    private fun stopPlayback() {
        activeSpeaker?.stop()
        activeSpeaker = null
        _ui.update { it.copy(playing = false) }
    }

    private fun watchNetwork() {
        val connectivity = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _ui.update { it.copy(networkAvailable = true) }
            }

            override fun onLost(network: Network) {
                _ui.update { it.copy(networkAvailable = false) }
            }
        }
        networkCallback = callback
        try {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        } catch (_: Throwable) {
            _ui.update { it.copy(networkAvailable = null) }
        }
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

    companion object {
        const val MAX_DRAFT = 80
        private const val TAG = "BlindDrive"
        private const val DEMO_SPEECH_START_MS = 2_000L
        private const val DEMO_SPEECH_LIMIT_MS = 12_000L
        private const val DEMO_PAUSE_MS = 800L
        private const val ALERT_HOLD_MS = 2_000L
    }
}
