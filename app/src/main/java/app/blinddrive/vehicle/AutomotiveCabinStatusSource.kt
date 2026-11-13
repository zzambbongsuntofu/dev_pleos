package app.blinddrive.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import app.blinddrive.model.CabinOrigin
import app.blinddrive.model.CabinSnapshot
import app.blinddrive.model.Motion

/**
 * Reads CarUxRestrictions from the vehicle service.
 * Until a value arrives, the snapshot stays unknown.
 */
class AutomotiveCabinStatusSource(
    private val context: Context,
) : CabinStatusSource {
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var onUpdate: ((CabinSnapshot) -> Unit)? = null

    override fun start(onUpdate: (CabinSnapshot) -> Unit) {
        this.onUpdate = onUpdate
        try {
            val automotive = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            if (!automotive) {
                emit(unavailable("이 기기에 차량 기능이 없습니다"))
                return
            }
            val created = Car.createCar(
                context,
                main,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                Car.CarServiceLifecycleListener { readyCar, ready ->
                    if (!ready) {
                        emit(unavailable("차량 서비스가 준비되지 않았습니다"))
                    } else {
                        bind(readyCar)
                    }
                },
            )
            if (created == null) {
                emit(unavailable("차량 서비스에 연결하지 못했습니다"))
            } else {
                car = created
            }
        } catch (throwable: Throwable) {
            emit(unavailable("차량 상태를 읽지 못했습니다"))
        }
    }

    override fun stop() {
        try {
            manager?.unregisterListener()
        } catch (_: Throwable) {
        }
        manager = null
        try {
            car?.disconnect()
        } catch (_: Throwable) {
        }
        car = null
        onUpdate = null
    }

    private fun bind(created: Car) {
        try {
            val resolved = created.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as? CarUxRestrictionsManager
            if (resolved == null) {
                emit(unavailable("주행 제한 서비스를 찾지 못했습니다"))
                return
            }
            manager = resolved
            resolved.registerListener { restrictions ->
                emit(fromRestrictions(restrictions))
            }
            val current = resolved.currentCarUxRestrictions
            if (current != null) {
                emit(fromRestrictions(current))
            } else {
                emit(unavailable("아직 주행 제한 값이 없습니다"))
            }
        } catch (_: Throwable) {
            emit(unavailable("주행 제한을 구독하지 못했습니다"))
        }
    }

    private fun fromRestrictions(restrictions: CarUxRestrictions): CabinSnapshot {
        val driving = restrictions.isRequiresDistractionOptimization
        return CabinSnapshot(
            origin = CabinOrigin.VEHICLE,
            motion = if (driving) Motion.DRIVING else Motion.PARKED,
            activeRestrictions = restrictions.activeRestrictions,
            detail = if (driving) {
                "차량 신호: 주행 제한"
            } else {
                "차량 신호: 주차"
            },
        )
    }

    private fun unavailable(detail: String) = CabinSnapshot(
        origin = CabinOrigin.VEHICLE_UNAVAILABLE,
        motion = Motion.UNKNOWN,
        activeRestrictions = 0,
        detail = detail,
    )

    private fun emit(snapshot: CabinSnapshot) {
        val callback = onUpdate ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(snapshot)
        } else {
            main.post { onUpdate?.invoke(snapshot) }
        }
    }
}
