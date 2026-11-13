package app.blinddrive.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener

/**
 * Says the six beginner lines from vehicle signals.
 * It does not read the lane position or the cars in the next lane.
 */
class BeginnerSignals(
    context: Context,
    private val onNews: (line: String, speak: Boolean) -> Unit,
    private val onLamps: (Set<BeginnerLamp>) -> Unit,
    private val onSteer: (angle: Float) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context.applicationContext)
    private val roadPace = RoadPace(context) { main.post { refresh() } }
    private var gear: VehicleGear? = null
    private var blinker = TurnSignalValue.OFF
    private var laneKeeping = false
    private val announced = mutableSetOf<BeginnerAdvice>()
    private var shownLamps = emptySet<BeginnerLamp>()
    private val speechQueue = ArrayDeque<String>()
    private var speaking = false
    private var started = false
    private var slowSince: Long? = null
    private var mergeSince: Long? = null
    private var blinkerSince: Long? = null
    private val speedSamples = ArrayDeque<SpeedSample>()
    private val harshTimes = ArrayDeque<Long>()

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            blinker = value ?: TurnSignalValue.OFF
            refresh()
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "Turn signal listener failed", exception)
        }
    }

    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            angle?.let(onSteer)
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "Steering listener failed", exception)
        }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            gear = value
            refresh()
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "Gear listener failed", exception)
        }
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(info: ai.pleos.playground.vehicle.api.model.telltale.Telltales?) {
            laneKeeping = info?.telltales.orEmpty().any { lamp ->
                lamp.type == TelltaleType.LKA &&
                    (lamp.state == TelltaleState.ON || lamp.state == TelltaleState.BLINK)
            }
            refresh()
        }

        override fun onFailed(exception: Exception) {
            Log.w(TAG, "Telltale listener failed", exception)
        }
    }

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
            started = true
            roadPace.start()
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle signals did not start", error)
            onNews("차량 신호를 읽지 못했습니다", false)
            return
        }
        listen("turn signal") {
            VehicleDomains.turnSignal(vehicle).registerTurnSignalState(turnListener)
        }
        listen("steering") {
            VehicleDomains.steeringWheel(vehicle).registerSteeringWheelAngle(steerListener)
        }
        listen("gear") {
            VehicleDomains.gear(vehicle).registerGearSelection(gearListener)
        }
        listen("telltales") {
            VehicleDomains.safety(vehicle).registerTelltales(telltaleListener)
        }
        onNews(BEGINNER_IDLE_LINE, false)
    }

    fun stop() {
        if (!started) return
        quietly { VehicleDomains.turnSignal(vehicle).unregisterTurnSignalState(turnListener) }
        quietly { VehicleDomains.steeringWheel(vehicle).unregisterSteeringWheelAngle(steerListener) }
        quietly { VehicleDomains.gear(vehicle).unregisterGearSelection(gearListener) }
        quietly { VehicleDomains.safety(vehicle).unregisterTelltales(telltaleListener) }
        quietly { vehicle.release() }
        roadPace.stop()
        started = false
    }

    /** Speed needs a permission the driver grants on screen. */
    fun retrySpeed() = roadPace.retrySpeed()

    private fun refresh() {
        val now = SystemClock.elapsedRealtime()
        val speed = roadPace.speedKmh
        val limit = roadPace.speedLimitKmh
        val moving = speed != null && speed >= MOVING_KMH
        val blinkerOn = blinker != TurnSignalValue.OFF
        val waitingToMerge = blinkerOn && moving && crowdedOrSlow(speed, limit)
        noteHarsh(now, speed)

        sync(BeginnerAdvice.LANE_LINE, laneKeeping)
        sync(BeginnerAdvice.MIRRORS, gear == VehicleGear.GEAR_REVERSE)
        hold(BeginnerAdvice.FLOW, slowForTheRoad(speed, limit, blinkerOn), now, SLOW_HOLD_MS, ::slowSince)
        hold(BeginnerAdvice.MERGE, waitingToMerge, now, MERGE_HOLD_MS, ::mergeSince)
        hold(BeginnerAdvice.BLINKER_OFF, blinkerOn && moving && !waitingToMerge, now, BLINKER_HOLD_MS, ::blinkerSince)
        sync(BeginnerAdvice.GAP, harshTimes.size >= HARSH_COUNT)
    }

    /** Below 60% of a limit of at least 60, while actually rolling and not signalling. */
    private fun slowForTheRoad(speed: Float?, limit: Int?, blinkerOn: Boolean): Boolean {
        if (blinkerOn || speed == null || limit == null || limit < ROAD_LIMIT_KMH) return false
        return speed >= SLOW_MIN_KMH && speed <= limit * SLOW_RATIO
    }

    /**
     * Trying to move over while the route is congested here, or while the car is well under
     * the limit. There is no count of cars in the next lane.
     */
    private fun crowdedOrSlow(speed: Float?, limit: Int?): Boolean {
        if (roadPace.crowded) return true
        if (speed == null || limit == null || limit < ROAD_LIMIT_KMH) return false
        return speed <= limit * MERGE_RATIO
    }

    private fun noteHarsh(now: Long, speed: Float?) {
        if (speed == null) return
        speedSamples.addLast(SpeedSample(now, speed))
        while (speedSamples.isNotEmpty() && now - speedSamples.first().at > SAMPLE_KEEP_MS) {
            speedSamples.removeFirst()
        }
        val past = speedSamples.lastOrNull { now - it.at >= HARSH_SPAN_MS } ?: return
        val delta = speed - past.speedKmh
        val harsh = delta <= -HARSH_DELTA_KMH || delta >= HARSH_DELTA_KMH
        val spaced = harshTimes.isEmpty() || now - harshTimes.last() > HARSH_SPAN_MS
        if (harsh && spaced) harshTimes.addLast(now)
        while (harshTimes.isNotEmpty() && now - harshTimes.first() > HARSH_WINDOW_MS) {
            harshTimes.removeFirst()
        }
    }

    private fun hold(
        advice: BeginnerAdvice,
        active: Boolean,
        now: Long,
        holdMs: Long,
        since: kotlin.reflect.KMutableProperty0<Long?>,
    ) {
        if (!active) {
            since.set(null)
            clear(advice)
            return
        }
        val startedAt = since.get() ?: now.also { since.set(it) }
        if (now - startedAt >= holdMs) sync(advice, true)
    }

    private fun sync(advice: BeginnerAdvice, active: Boolean) {
        if (active) say(advice) else clear(advice)
    }

    private fun clear(advice: BeginnerAdvice) {
        if (announced.remove(advice)) publishLamps()
    }

    private fun publishLamps() {
        val lit = announced.mapNotNullTo(mutableSetOf()) { it.lamp }
        if (lit == shownLamps) return
        shownLamps = lit
        onLamps(lit)
    }

    private fun say(advice: BeginnerAdvice) {
        if (!announced.add(advice)) return
        Log.i(TAG, "advice ${advice.name}")
        publishLamps()
        if (speechQueue.contains(advice.line)) return
        speechQueue.addLast(advice.line)
        pump()
    }

    private fun pump() {
        if (speaking) return
        val line = speechQueue.removeFirstOrNull() ?: return
        speaking = true
        onNews(line, true)
        main.postDelayed({
            speaking = false
            pump()
        }, SPEECH_GAP_MS)
    }

    private fun listen(name: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            Log.w(TAG, "$name did not start", error)
        }
    }

    private fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle signal did not stop", error)
        }
    }

    private data class SpeedSample(val at: Long, val speedKmh: Float)

    private companion object {
        const val TAG = "BlindDrive"
        const val MOVING_KMH = 5f
        const val SLOW_MIN_KMH = 15f
        const val ROAD_LIMIT_KMH = 60
        const val SLOW_RATIO = 0.6f
        const val MERGE_RATIO = 0.7f
        const val SLOW_HOLD_MS = 8_000L
        const val MERGE_HOLD_MS = 6_000L
        const val BLINKER_HOLD_MS = 8_000L
        const val HARSH_DELTA_KMH = 12f
        const val HARSH_SPAN_MS = 2_000L
        const val HARSH_WINDOW_MS = 20_000L
        const val HARSH_COUNT = 3
        const val SAMPLE_KEEP_MS = 5_000L
        const val SPEECH_GAP_MS = 6_000L
    }
}
