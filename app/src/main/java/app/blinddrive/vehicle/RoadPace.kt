package app.blinddrive.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/**
 * Current speed comes from the vehicle speed property, in meters per second.
 * Road size is inferred from the navigation speed limit. There is no road-class value.
 */
class RoadPace(context: Context, private val onPace: () -> Unit = {}) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var speeds: CarPropertyManager? = null
    private var navigation: NaviHelper? = null

    @Volatile
    var speedKmh: Float? = null
        private set

    @Volatile
    var speedLimitKmh: Int? = null
        private set

    /**
     * True when navigation reports a slow or heavy stretch at the car.
     * The congestion offset has no documented unit, so 100 or below is treated as "here".
     * This is the route's traffic, not a count of cars in the next lane.
     */
    @Volatile
    var crowded: Boolean = false
        private set

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val metersPerSecond = (value.value as? Float) ?: return
            speedKmh = metersPerSecond * 3.6f
            onPace()
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.w(TAG, "Vehicle speed property failed: $propId")
        }
    }

    private val navigationListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            speedLimitKmh = speedLimit.takeIf { it > 0 }
            onPace()
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            val near = info.trafficStatus?.congestionItemList.orEmpty().any { item ->
                val type = item.trafficCongestionType
                item.offset <= NEAR_OFFSET &&
                    (type == TrafficCongestionType.SLOW || type == TrafficCongestionType.HEAVY)
            }
            if (near == crowded) return
            crowded = near
            Log.i(TAG, "route congestion near=$near")
            onPace()
        }
    }

    fun start() {
        startSpeed()
        startSpeedLimit()
    }

    fun stop() {
        try {
            speeds?.unregisterCallback(speedCallback)
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle speed did not stop", error)
        }
        speeds = null
        try {
            car?.disconnect()
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle connection did not stop", error)
        }
        car = null
        try {
            navigation?.removeListener(navigationListener)
            navigation?.release()
        } catch (error: Throwable) {
            Log.w(TAG, "Navigation speed limit did not stop", error)
        }
        navigation = null
    }

    /** Speed needs a permission the driver grants on screen. Call this after it is granted. */
    fun retrySpeed() {
        val connected = car?.takeIf { it.isConnected } ?: return
        bindSpeed(connected)
    }

    private fun startSpeed() {
        try {
            val created = Car.createCar(
                appContext,
                main,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { readyCar, ready ->
                    if (!ready) return@CarServiceLifecycleListener
                    bindSpeed(readyCar)
                },
            )
            car = created
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle speed did not start", error)
        }
    }

    private fun bindSpeed(readyCar: Car) {
        try {
            val manager = readyCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (manager == null) {
                Log.w(TAG, "Vehicle speed property manager is missing")
                return
            }
            speeds = manager
            manager.unregisterCallback(speedCallback)
            manager.registerCallback(
                speedCallback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_NORMAL,
            )
            val metersPerSecond = manager.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            speedKmh = metersPerSecond * 3.6f
            Log.i(TAG, "speed bound")
            onPace()
        } catch (error: Throwable) {
            Log.w(TAG, "Vehicle speed did not bind", error)
        }
    }

    private fun startSpeedLimit() {
        try {
            val helper = NaviHelper(appContext)
            helper.initialize()
            helper.addListener(navigationListener)
            navigation = helper
        } catch (error: Throwable) {
            Log.w(TAG, "Navigation speed limit did not start", error)
        }
    }

    private companion object {
        const val TAG = "BlindDrive"
        const val NEAR_OFFSET = 100.0
    }
}
