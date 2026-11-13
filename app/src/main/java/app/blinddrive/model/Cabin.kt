package app.blinddrive.model

enum class CabinOrigin {
    VEHICLE,
    VEHICLE_UNAVAILABLE,
    LOCAL_MOCK,
}

enum class Motion {
    PARKED,
    DRIVING,
    UNKNOWN,
}

data class CabinSnapshot(
    val origin: CabinOrigin,
    val motion: Motion,
    val activeRestrictions: Int,
    val detail: String,
)
