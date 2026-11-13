package app.blinddrive.story

data class SampleStory(
    val id: String,
    val text: String,
)

data class LocalSubmission(
    val text: String,
    val confirmedOnDevice: Boolean = true,
    val anonymized: Boolean = false,
    val reviewed: Boolean = false,
    val sentToServer: Boolean = false,
)

object SampleStories {
    val items: List<SampleStory> = listOf(
        SampleStory(
            id = "sample-1",
            text = "퇴근길에 노을이 길었습니다. 창문을 조금 열고 집에 갔습니다.",
        ),
        SampleStory(
            id = "sample-2",
            text = "비 오는 날, 신호등 앞에서 처음 듣는 노래를 흥얼거렸습니다.",
        ),
        SampleStory(
            id = "sample-3",
            text = "새벽에 도로가 비어 있어 숨이 깊어졌습니다.",
        ),
    )
}
