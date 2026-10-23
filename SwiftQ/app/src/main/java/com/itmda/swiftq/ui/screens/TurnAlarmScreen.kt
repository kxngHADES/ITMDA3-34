package com.itmda.swiftq.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme

private val TurnWhite = Color.White

/**
 * Figma 15 · It's your turn. Full-screen, shown over everything while the app is open and the ticket is called
 * (and when the patient opens the app from the "your turn" push). [roomLabel] is the counter or room the API names
 * (`counter_label`); when it has none, the pill says to go in now. [onComing] dismisses it: the Home screen keeps
 * showing "It's your turn" underneath.
 */
@Composable
fun TurnAlarmScreen(
    ticketNumber: String,
    spokenNumber: String?,
    roomLabel: String?,
    onComing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    val pulse = rememberInfiniteTransition(label = "turn-pulse")
    val outer by pulse.animateFloat(1f, 1.07f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "outer")
    val ring by pulse.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1100, delayMillis = 150), RepeatMode.Reverse), label = "ring")

    Column(
        modifier = modifier.fillMaxSize().background(colors.blue).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp).scale(outer).background(TurnWhite.copy(alpha = 0.12f), CircleShape)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(136.dp).scale(ring).background(TurnWhite.copy(alpha = 0.20f), CircleShape)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp).background(TurnWhite, CircleShape)) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.blue, modifier = Modifier.size(52.dp))
                    }
                }
            }
            Text(
                text = stringResource(R.string.turn_alarm_title),
                style = SwiftQTextStyles.LargeTitle, color = TurnWhite, textAlign = TextAlign.Center,
                // Spoken as soon as it appears, even if the screen reader is reading something else.
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive },
            )
            Text(
                text = ticketNumber,
                style = SwiftQTextStyles.LargeTitle.copy(fontSize = 72.sp, lineHeight = 84.sp, fontWeight = FontWeight.Bold),
                color = TurnWhite,
                modifier = Modifier.semantics { if (spokenNumber != null) contentDescription = spokenNumber },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TurnWhite.copy(alpha = 0.18f))
                    .padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 14.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Icon(painterResource(R.drawable.ic_tab_home), contentDescription = null, tint = TurnWhite, modifier = Modifier.size(30.dp))
                Text(
                    text = roomLabel?.let { stringResource(R.string.turn_alarm_room, it) } ?: stringResource(R.string.turn_alarm_room_fallback),
                    style = SwiftQTextStyles.Title1, color = TurnWhite, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TurnWhite)
                .clickable(role = Role.Button, onClick = onComing),
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.blueText, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.turn_alarm_on_my_way), style = SwiftQTextStyles.Title3, color = colors.blueText)
        }
        Text(
            text = stringResource(R.string.turn_alarm_hint),
            style = SwiftQTextStyles.Body, color = TurnWhite.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp),
        )
    }
}

@Preview(widthDp = 393, heightDp = 852)
@Composable
private fun TurnAlarmScreenPreview() {
    SwiftQTheme(darkTheme = false) {
        TurnAlarmScreen(ticketNumber = "A-042", spokenNumber = "Ticket number A, 0 4 2", roomLabel = "Room 3", onComing = {})
    }
}
