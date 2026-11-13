package app.blinddrive.vehicle

/** Shown and never spoken on its own, while nothing is wrong. */
const val BEGINNER_IDLE_LINE = "놓친 게 있으면 말해 드릴게요"

/**
 * The six lines the app says, with the lamp each one lights. Detection and the debug
 * walk-through both read from here.
 */
enum class BeginnerAdvice(val keyword: String, val line: String, val lamp: BeginnerLamp?) {
    LANE_LINE("차선", "차선을 밟고 있는지 확인하세요", null),
    FLOW("흐름", "주변 차들과 흐름을 맞추세요", null),
    BLINKER_OFF("깜빡이", "깜빡이를 꺼도 돼요", BeginnerLamp.BLINKER),
    MERGE("끼어들기", "끼어들지 못해도 괜찮아요", BeginnerLamp.BLINKER),
    GAP("간격", "앞차와의 간격을 넉넉히 두세요", null),
    MIRRORS("미러", "사이드미러, 백미러도 같이 보세요", BeginnerLamp.GEAR),
}
