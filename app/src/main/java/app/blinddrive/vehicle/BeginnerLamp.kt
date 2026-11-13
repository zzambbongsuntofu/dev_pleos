package app.blinddrive.vehicle

/** One light on the screen. It is lit only while a beginner mistake is detected. */
enum class BeginnerLamp(val label: String) {
    BELT("안전벨트"),
    GEAR("기어"),
    PARKING_BRAKE("주차 브레이크"),
    HEADLIGHT("전조등"),
    HIGH_BEAM("상향등"),
    BLINKER("깜빡이"),
    WIPER("와이퍼"),
    WARNING("경고등"),

    /** Drawn as the steering gauge, not as a tile. */
    WHEEL("핸들"),
}
