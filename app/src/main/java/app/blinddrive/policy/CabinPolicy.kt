package app.blinddrive.policy

import app.blinddrive.model.CabinSnapshot
import app.blinddrive.model.Motion

data class FeatureGate(
    val allowed: Boolean,
    val reason: String,
)

/**
 * Parked, driving, and unknown are separate.
 * Distraction Optimization is not declared, so a driving signal blocks input,
 * listening, and voting. An unreadable signal stays restricted.
 *
 * Bit values match the regional driving-restriction policy checked on 2026-11-05
 * and the CarUxRestrictions constants those pages name.
 */
object CabinPolicy {
    const val NO_DIALPAD = 1
    const val NO_FILTERING = 2
    const val LIMIT_STRING_LENGTH = 4
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val LIMIT_CONTENT = 32
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
    const val NO_VOICE_TRANSCRIPTION = 256

    private val textEntryMask = NO_KEYBOARD or LIMIT_STRING_LENGTH or LIMIT_CONTENT

    fun storyInput(snapshot: CabinSnapshot): FeatureGate {
        blockedMotion(snapshot)?.let { return it }
        if (snapshot.activeRestrictions and NO_VOICE_TRANSCRIPTION != 0) {
            return FeatureGate(false, "음성 받아쓰기가 제한되어 있습니다")
        }
        return FeatureGate(true, "주차 확인. 말로 사연을 넣을 수 있습니다")
    }

    fun storyEdit(snapshot: CabinSnapshot): FeatureGate {
        blockedMotion(snapshot)?.let { return it }
        if (snapshot.activeRestrictions and textEntryMask != 0) {
            return FeatureGate(false, "글자 수정은 제한되어 있습니다")
        }
        return FeatureGate(true, "짧은 수정이 가능합니다")
    }

    fun storySubmit(snapshot: CabinSnapshot): FeatureGate {
        blockedMotion(snapshot)?.let { return it }
        if (snapshot.activeRestrictions and NO_TEXT_MESSAGE != 0) {
            return FeatureGate(false, "글 전송이 제한되어 있습니다")
        }
        return FeatureGate(true, "이 기기 대기열에 둘 수 있습니다")
    }

    fun listen(snapshot: CabinSnapshot): FeatureGate {
        blockedMotion(snapshot)?.let { return it }
        return FeatureGate(true, "주차 확인. 샘플 사연을 읽을 수 있습니다")
    }

    fun vote(snapshot: CabinSnapshot): FeatureGate {
        blockedMotion(snapshot)?.let { return it }
        return FeatureGate(true, "주차 확인. A 또는 B를 고를 수 있습니다")
    }

    fun describeRestrictions(bits: Int): String {
        if (bits == 0) return "추가 제한 없음"
        val names = buildList {
            if (bits and NO_DIALPAD != 0) add("다이얼패드")
            if (bits and NO_FILTERING != 0) add("필터")
            if (bits and LIMIT_STRING_LENGTH != 0) add("글자 수")
            if (bits and NO_KEYBOARD != 0) add("키보드")
            if (bits and NO_VIDEO != 0) add("영상")
            if (bits and LIMIT_CONTENT != 0) add("긴 내용")
            if (bits and NO_SETUP != 0) add("설정")
            if (bits and NO_TEXT_MESSAGE != 0) add("글 전송")
            if (bits and NO_VOICE_TRANSCRIPTION != 0) add("음성 받아쓰기")
        }
        return names.joinToString(", ")
    }

    private fun blockedMotion(snapshot: CabinSnapshot): FeatureGate? = when (snapshot.motion) {
        Motion.DRIVING -> FeatureGate(false, "주행 중에는 사용할 수 없습니다")
        Motion.UNKNOWN -> FeatureGate(false, "상태를 확인하지 못했습니다")
        Motion.PARKED -> null
    }
}
