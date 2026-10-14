package com.itmda.swiftq.ui.screens

import com.itmda.swiftq.ui.status.spokenTicketNumber
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itmda.swiftq.R
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.ui.components.LastUpdatedText
import com.itmda.swiftq.ui.components.LogoLockup
import com.itmda.swiftq.ui.components.NoticeCard
import com.itmda.swiftq.ui.components.SwiftQTab
import com.itmda.swiftq.ui.components.SwiftQTabBar
import com.itmda.swiftq.ui.status.StatusUi
import com.itmda.swiftq.ui.status.formatTicketNumber
import com.itmda.swiftq.ui.status.savedText
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme

// Figma "Your number": 76sp bold, 88 line height, -1.5% tracking.
private val TicketNumberStyle = SwiftQTextStyles.LargeTitle.copy(
    fontSize = 76.sp, lineHeight = 88.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.14).sp,
)

// Figma status/error/text. P1's theme has no error colour token yet.
private val LeaveRed = Color(0xFFD70015)

/** Home for a patient who holds a ticket (Figma frame 03). Read from SQLite, so it also opens offline. */
@Composable
fun HomeActiveTicketScreen(
    ui: StatusUi,
    ticket: TicketEntity,
    onSeeLiveQueue: () -> Unit,
    onLeave: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    val number = formatTicketNumber(ticket.displayNumber)
    val spokenNumber = spokenTicketNumber(ticket.displayNumber)?.let { stringResource(R.string.a11y_ticket_number, it) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp)) {
            LogoLockup(modifier = Modifier.padding(top = 4.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(40.dp))
                Text(stringResource(R.string.status_your_number), style = SwiftQTextStyles.Title3, color = colors.secondary)
                Text(
                    text = number ?: stringResource(R.string.status_number_pending),
                    style = if (number != null) TicketNumberStyle else SwiftQTextStyles.Title1,
                    color = colors.blue,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { if (spokenNumber != null) contentDescription = spokenNumber },
                )
                ui.clinicName?.let { Text(it, style = SwiftQTextStyles.Body, color = colors.secondary, textAlign = TextAlign.Center) }

                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (ticket.status == TicketState.PENDING_SYNC) NoticeCard(R.drawable.ic_cloud_off, savedText(ui.savedReason))
                    // Once called, the API sends no position or wait, so say what matters instead of "Not known yet".
                    if (ticket.status == TicketState.CALLED) NoticeCard(
                        R.drawable.ic_bell,
                        ticket.counterLabel?.let { stringResource(R.string.status_state_called_at, it) } ?: stringResource(R.string.status_state_called),
                    )
                    if (ticket.status != TicketState.CALLED) StatCard(
                        R.drawable.ic_people,
                        title = ticket.peopleAhead?.let { pluralStringResource(R.plurals.status_people_count, it, it) }
                            ?: stringResource(R.string.status_people_unknown),
                        subtitle = stringResource(R.string.status_people_label),
                    )
                    val min = ticket.waitMinMinutes
                    val max = ticket.waitMaxMinutes
                    if (ticket.status != TicketState.CALLED) StatCard(
                        R.drawable.ic_clock,
                        title = if (min != null && max != null) stringResource(R.string.home_wait_range, min, max) else stringResource(R.string.status_wait_unknown),
                        subtitle = stringResource(if (ticket.waitColdStart == true) R.string.home_wait_label_estimate else R.string.status_wait_label),
                    )
                    // A saved place has had no server answer yet, so there is nothing to date.
                    if (ticket.status != TicketState.PENDING_SYNC) LastUpdatedText(ticket.lastSyncedAt)
                }

                // Only a ticket the server has confirmed, and not yet called, is in the live queue.
                if (ticket.status == TicketState.WAITING) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.5.dp, colors.blue), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onSeeLiveQueue)
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_queue), contentDescription = null, tint = colors.blue)
                    Text(stringResource(R.string.home_see_live_queue), style = SwiftQTextStyles.Title3, color = colors.blueText)
                    Spacer(Modifier.weight(1f))
                }
                }
                Spacer(Modifier.height(16.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (ticket.status == TicketState.WAITING) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.status_alert_hint), style = SwiftQTextStyles.Body, color = colors.secondary)
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onLeave),
                ) {
                    Text(stringResource(R.string.home_leave_queue), style = SwiftQTextStyles.Headline, color = LeaveRed)
                }
            }
        }

        SwiftQTabBar(
            selectedTab = SwiftQTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    SwiftQTab.Home -> Unit
                    SwiftQTab.Profile -> onProfile()
                    SwiftQTab.Settings -> onSettings()
                }
            },
        )
    }

}

/** Figma "3 people / before you" card: a 56dp white badge, a 28sp title and a body line. */
@Composable
private fun StatCard(iconRes: Int, title: String, subtitle: String) {
    val colors = SwiftQTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.grouped).padding(16.dp).semantics(mergeDescendants = true) {},
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).background(colors.surface, RoundedCornerShape(16.dp))) {
            Icon(painterResource(iconRes), contentDescription = null, tint = colors.blue, modifier = Modifier.size(30.dp))
        }
        Column {
            Text(title, style = SwiftQTextStyles.Title1, color = colors.label)
            Text(subtitle, style = SwiftQTextStyles.Body, color = colors.secondary)
        }
    }
}
