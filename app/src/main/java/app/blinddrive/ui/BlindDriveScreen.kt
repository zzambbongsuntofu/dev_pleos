package app.blinddrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blinddrive.R
import app.blinddrive.vehicle.BeginnerAdvice
import app.blinddrive.vehicle.BeginnerLamp

/** Same cream as the background of the passenger pictures, so their edges do not show. */
private val Paper = Color(0xFFFFF9EB)
private val Ink = Color(0xFF1C2430)
private val LampLit = Color(0xFFF2A12B)
private val LampOff = Ink.copy(alpha = 0.06f)
private val LampOffText = Ink.copy(alpha = 0.38f)
private val DemoNote = Color(0xFFA85A00)
private val WheelDot = 32.dp
private val TileHeight = 76.dp

/**
 * The advice fills the screen on its own. Nothing else competes with it, so it can be read
 * in a glance. The text stays still; blinking it would make it harder to read.
 */
@Composable
private fun AdviceTakeover(state: UiState, viewModel: BlindDriveViewModel) {
    Box(modifier = Modifier.fillMaxSize().background(Paper)) {
        val advice = BeginnerAdvice.entries.find { it.line == state.voiceLine }
        val keyword = advice?.keyword ?: state.voiceLine
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            val perLetter = maxWidth / keyword.length.coerceAtLeast(1)
            val keywordSize = (perLetter.value * 0.82f).coerceIn(200f, 320f).sp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = keyword,
                    color = Ink,
                    fontSize = keywordSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (advice != null) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = advice.line,
                        color = Ink.copy(alpha = 0.72f),
                        fontSize = 44.sp,
                        lineHeight = 58.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Image(
            painter = painterResource(passengerPicture(state)),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).height(200.dp),
            contentScale = ContentScale.Fit,
        )
        if (state.demo) {
            Text(
                text = "시연입니다. 실제 차량 신호가 아닙니다",
                color = Ink.copy(alpha = 0.7f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp),
            )
            DemoButton(
                text = "시연 멈춤",
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 8.dp),
                onClick = viewModel::toggleDemo,
            )
        }
    }
}

/** The gauge reaches its edge at half a turn. Lane-keeping corrections are far smaller. */
private const val WHEEL_GAUGE_DEGREES = 180f

@Composable
fun BlindDriveScreen(viewModel: BlindDriveViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    if (state.alert) {
        AdviceTakeover(state, viewModel)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "초보운전",
                color = Ink.copy(alpha = 0.62f),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            if (viewModel.demoAvailable) {
                DemoButton(
                    text = if (state.demo) "시연 멈춤" else "시연",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = viewModel::toggleDemo,
                )
            }
        }
        if (state.demo) {
            Text(
                text = "시연입니다. 실제 차량 신호가 아닙니다",
                color = DemoNote,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.voiceLine,
                color = Ink,
                fontSize = 48.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = TextStyle(lineBreak = LineBreak.Heading),
            )
        }
        Image(
            painter = painterResource(passengerPicture(state)),
            contentDescription = null,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(16.dp))
        WheelGauge(
            angle = state.steerAngle,
            alert = BeginnerLamp.WHEEL in state.lamps,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LampBoard(lit = state.lamps, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        PracticeButton(
            text = if (state.playing) "멈춤" else "다시 듣기",
            onClick = viewModel::togglePractice,
        )
    }
}

/**
 * The person in the passenger seat. Calm when nothing is wrong, worried for one mistake or
 * while an advice line is spoken, pointing when two or more mistakes are lit. The picture is
 * swapped, not animated.
 */
private fun passengerPicture(state: UiState): Int = when {
    state.lamps.size >= 2 -> R.drawable.passenger_pointing
    state.lamps.isNotEmpty() || (state.playing && state.lastAdvice != null) ->
        R.drawable.passenger_worried
    else -> R.drawable.passenger_calm
}

/**
 * Shows where the steering wheel is, with the middle marked. It is the wheel angle the car
 * reports. It is not the position in the lane; the app cannot read that.
 */
@Composable
private fun WheelGauge(angle: Float?, alert: Boolean, modifier: Modifier) {
    val fill by animateColorAsState(if (alert) LampLit else LampOff, label = "wheelFill")
    Row(
        modifier = modifier
            .height(TileHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            text = BeginnerLamp.WHEEL.label,
            color = if (alert) Ink else Ink.copy(alpha = 0.62f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Ink.copy(alpha = 0.18f)),
            )
            Box(Modifier.width(4.dp).height(36.dp).background(Ink.copy(alpha = 0.45f)))
            if (angle != null) {
                val reach = (maxWidth - WheelDot) / 2
                val position = (angle / WHEEL_GAUGE_DEGREES).coerceIn(-1f, 1f)
                Box(
                    Modifier
                        .offset(x = reach * position)
                        .size(WheelDot)
                        .clip(CircleShape)
                        .background(Ink),
                )
            }
        }
    }
}

/** Lamps stay dim. One lights up only while its mistake is detected. */
@Composable
private fun LampBoard(lit: Set<BeginnerLamp>, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val columns = if (maxWidth >= 1200.dp) 8 else 4
        val rows = BeginnerLamp.entries.filter { it != BeginnerLamp.WHEEL }.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(TileHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { lamp ->
                        LampTile(
                            label = lamp.label,
                            lit = lamp in lit,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LampTile(label: String, lit: Boolean, modifier: Modifier) {
    val fill by animateColorAsState(if (lit) LampLit else LampOff, label = "lampFill")
    val text by animateColorAsState(if (lit) Ink else LampOffText, label = "lampText")
    Box(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Debug builds only. Starts or stops the walk-through of every advice line. */
@Composable
private fun DemoButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(LampOff)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Ink.copy(alpha = 0.62f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PracticeButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(min = 420.dp)
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Paper,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
