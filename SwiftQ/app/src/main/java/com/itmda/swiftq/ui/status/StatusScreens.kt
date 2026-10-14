package com.itmda.swiftq.ui.status

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.ui.components.InfoCard
import com.itmda.swiftq.ui.components.LastUpdatedText
import com.itmda.swiftq.ui.components.NoticeCard
import com.itmda.swiftq.ui.components.PrimaryButton
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme

/** "A014" from the API is shown as "A-014", as in the Figma frames. */
fun formatTicketNumber(raw: String?): String? {
    if (raw == null) return null
    val m = Regex("^([A-Z]{1,2})(\\d+)$").matchEntire(raw) ?: return raw
    return "${m.groupValues[1]}-${m.groupValues[2]}"
}

/**
 * How a screen reader should say a ticket number. "A-014" is read as one odd word, so it becomes "A, 0 1 4".
 * Null when there is no number yet.
 */
fun spokenTicketNumber(raw: String?): String? {
    val shown = formatTicketNumber(raw) ?: return null
    val m = Regex("^([A-Z]{1,2})-(\\d+)$").matchEntire(shown) ?: return shown
    return m.groupValues[1].toList().joinToString(" ") + ", " + m.groupValues[2].toList().joinToString(" ")
}

@Composable
internal fun savedText(reason: SavedReason?): String = stringResource(
    when (reason) {
        SavedReason.Paused -> R.string.status_state_saved_paused
        SavedReason.Unreachable -> R.string.status_state_saved_unreachable
        else -> R.string.status_state_saved
    },
)

/** Figma 07 · You're in. Before the server has confirmed, it says the place is saved instead of showing a number. */
@Composable
fun JoinedScreen(ui: StatusUi, onSeeTicket: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SwiftQTheme.colors
    val ticket = ui.ticket
    val confirmed = ticket != null && ticket.status != TicketState.PENDING_SYNC && ticket.status != TicketState.FAILED
    Column(
        modifier = modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(148.dp).background(colors.blue50, CircleShape)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(108.dp).background(colors.blue, CircleShape)) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
            Text(
                text = stringResource(if (confirmed) R.string.joined_title else R.string.joined_saved_title),
                style = SwiftQTextStyles.Title1, color = colors.label, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            if (confirmed && ticket != null) {
                Text(stringResource(R.string.joined_your_number), style = SwiftQTextStyles.Headline, color = colors.secondary, modifier = Modifier.padding(top = 24.dp))
                val spoken = stringResource(R.string.a11y_ticket_number, spokenTicketNumber(ticket.displayNumber).orEmpty())
                Text(
                    formatTicketNumber(ticket.displayNumber).orEmpty(), style = SwiftQTextStyles.LargeTitle, color = colors.blue,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
                ticket.peopleAhead?.let {
                    Text(
                        text = pluralStringResource(R.plurals.status_people_count, it, it) + " " + stringResource(R.string.status_people_label),
                        style = SwiftQTextStyles.Headline, color = colors.label,
                    )
                }
            } else {
                Text(
                    text = if (ticket?.status == TicketState.FAILED) failureText(ui) else savedText(ui.savedReason),
                    style = SwiftQTextStyles.Body, color = colors.secondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        PrimaryButton(
            label = stringResource(R.string.joined_see_ticket),
            onClick = onSeeTicket,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun failureText(ui: StatusUi): String = ui.failureMessage ?: stringResource(R.string.status_state_failed_generic)

/** Figma 03 · the active ticket: number, people before you, wait range and the queue state. */
@Composable
fun TicketStatusScreen(
    ui: StatusUi,
    onChooseAnother: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    val ticket = ui.ticket
    Column(
        modifier = modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 32.dp),
        ) {
            if (ticket == null) {
                if (ui.loaded) Text(stringResource(R.string.status_empty), style = SwiftQTextStyles.Body, color = colors.secondary)
                return@Column
            }
            Text(stringResource(R.string.status_your_number), style = SwiftQTextStyles.Headline, color = colors.secondary)
            val number = formatTicketNumber(ticket.displayNumber)
            val spokenNumber = spokenTicketNumber(ticket.displayNumber)?.let { stringResource(R.string.a11y_ticket_number, it) }
            Text(
                text = number ?: stringResource(R.string.status_number_pending),
                style = if (number != null) SwiftQTextStyles.LargeTitle else SwiftQTextStyles.Title1,
                color = colors.blue,
                modifier = Modifier.padding(top = 4.dp).semantics { if (spokenNumber != null) contentDescription = spokenNumber },
            )
            val place = listOfNotNull(ui.clinicName, ui.servicePointName).joinToString(" · ")
            if (place.isNotEmpty()) Text(place, style = SwiftQTextStyles.Subhead, color = colors.secondary, textAlign = TextAlign.Center)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 24.dp)) {
                StateNotice(ui, ticket)
                // Position and wait only exist while waiting; once called (or finished) the API sends none.
                if (ticket.status == TicketState.WAITING || ticket.status == TicketState.PENDING_SYNC) {
                    InfoCard(
                        R.drawable.ic_people,
                        title = ticket.peopleAhead?.let { pluralStringResource(R.plurals.status_people_count, it, it) } ?: stringResource(R.string.status_people_unknown),
                        subtitle = stringResource(R.string.status_people_label),
                    )
                    InfoCard(R.drawable.ic_clock, title = waitText(ticket), subtitle = stringResource(R.string.status_wait_label))
                }
                if (ticket.status != TicketState.PENDING_SYNC) LastUpdatedText(ticket.lastSyncedAt)
            }
            if (ticket.status == TicketState.WAITING) {
                AlertHint()
            }
            if (ticket.status == TicketState.FAILED) {
                PrimaryButton(
                    label = stringResource(R.string.status_choose_another),
                    onClick = onChooseAnother,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
        }
        PrimaryButton(
            label = stringResource(R.string.status_back_home),
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun AlertHint() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 20.dp),
    ) {
        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, tint = SwiftQTheme.colors.tertiary, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.status_alert_hint), style = SwiftQTextStyles.Subhead, color = SwiftQTheme.colors.secondary)
    }
}

@Composable
internal fun waitText(ticket: TicketEntity): String {
    val min = ticket.waitMinMinutes
    val max = ticket.waitMaxMinutes
    if (min == null || max == null) return stringResource(R.string.status_wait_unknown)
    // While the server is still on its default base time the range is only a guess, so say so.
    return if (ticket.waitColdStart == true) stringResource(R.string.status_wait_range_estimate, min, max)
    else stringResource(R.string.status_wait_range, min, max)
}

@Composable
private fun StateNotice(ui: StatusUi, ticket: TicketEntity) {
    when (ticket.status) {
        TicketState.PENDING_SYNC -> NoticeCard(R.drawable.ic_cloud_off, savedText(ui.savedReason))
        TicketState.FAILED -> NoticeCard(R.drawable.ic_info, failureText(ui))
        TicketState.WAITING -> NoticeCard(R.drawable.ic_check_circle, stringResource(R.string.status_state_waiting))
        TicketState.CALLED -> NoticeCard(
            R.drawable.ic_bell,
            ticket.counterLabel?.let { stringResource(R.string.status_state_called_at, it) } ?: stringResource(R.string.status_state_called),
        )
        TicketState.SERVED -> NoticeCard(R.drawable.ic_check_circle, stringResource(R.string.status_state_served))
        TicketState.NO_SHOW -> NoticeCard(R.drawable.ic_info, stringResource(R.string.status_state_no_show))
        TicketState.CANCELLED -> NoticeCard(R.drawable.ic_info, stringResource(R.string.status_state_cancelled))
    }
}
