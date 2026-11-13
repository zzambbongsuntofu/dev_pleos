package app.blinddrive.vehicle

import app.blinddrive.model.CabinOrigin
import app.blinddrive.model.CabinSnapshot
import app.blinddrive.model.Motion
import app.blinddrive.policy.CabinPolicy

enum class MockMotion {
    PARKED,
    DRIVING,
    UNKNOWN,
}

/** Local stand-in. These snapshots are not vehicle signals. */
class MockCabinStatusSource {
    fun snapshot(mode: MockMotion): CabinSnapshot = when (mode) {
        MockMotion.PARKED -> CabinSnapshot(
            origin = CabinOrigin.LOCAL_MOCK,
            motion = Motion.PARKED,
            activeRestrictions = 0,
            detail = "개발용 모의 주차. 차량 신호가 아닙니다.",
        )

        MockMotion.DRIVING -> CabinSnapshot(
            origin = CabinOrigin.LOCAL_MOCK,
            motion = Motion.DRIVING,
            activeRestrictions = CabinPolicy.NO_KEYBOARD or
                CabinPolicy.LIMIT_STRING_LENGTH or
                CabinPolicy.NO_VIDEO or
                CabinPolicy.LIMIT_CONTENT or
                CabinPolicy.NO_VOICE_TRANSCRIPTION,
            detail = "개발용 모의 주행. 차량 신호가 아닙니다.",
        )

        MockMotion.UNKNOWN -> CabinSnapshot(
            origin = CabinOrigin.LOCAL_MOCK,
            motion = Motion.UNKNOWN,
            activeRestrictions = 0,
            detail = "개발용 모의 상태 불명. 차량 신호가 아닙니다.",
        )
    }
}
