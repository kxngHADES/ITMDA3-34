package com.itmda.swiftq.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.data.local.TicketEntity
import com.itmda.swiftq.data.local.TicketState
import com.itmda.swiftq.ui.components.FlowScaffold
import com.itmda.swiftq.ui.components.InfoCard
import com.itmda.swiftq.ui.components.PrimaryButton
import com.itmda.swiftq.ui.components.StateMessage
import com.itmda.swiftq.ui.status.formatTicketNumber
import com.itmda.swiftq.ui.status.spokenTicketNumber
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Settings > Ticket history: the patient's finished tickets, each removable, with Clear all. */
@Composable
fun HistoryScreen(
    ui: HistoryUi,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowScaffold(
        title = stringResource(R.string.history_title),
        subtitle = stringResource(R.string.history_subtitle),
        onBack = onBack,
        modifier = modifier,
        bottom = {
            if (ui.items.isNotEmpty()) {
                PrimaryButton(label = stringResource(R.string.history_clear), onClick = onClearAll, modifier = Modifier.fillMaxWidth())
            }
        },
    ) {
        if (ui.loaded && ui.items.isEmpty()) {
            StateMessage(R.drawable.ic_ticket_outline, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
            return@FlowScaffold
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.items.forEach { item -> HistoryRow(item, onRemove) }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onRemove: (String) -> Unit) {
    val t = item.ticket
    val title = formatTicketNumber(t.displayNumber) ?: stringResource(R.string.status_number_pending)
    val status = stringResource(statusLabel(t.status))
    val subtitle = listOfNotNull(status, item.clinicName, dateText(t)).joinToString(" · ")
    val spoken = stringResource(R.string.a11y_ticket_number, spokenTicketNumber(t.displayNumber) ?: title) + ", " + subtitle
    Column(modifier = Modifier.fillMaxWidth()) {
        InfoCard(iconFor(t.status), title = title, subtitle = subtitle, contentDescription = spoken)
        TextButton(onClick = { onRemove(t.localRef) }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).padding(top = 0.dp)) {
            Text(stringResource(R.string.history_remove), style = SwiftQTextStyles.Subhead, color = SwiftQTheme.colors.blueText)
        }
    }
}

internal fun statusLabel(status: String): Int = when (status) {
    TicketState.SERVED -> R.string.history_status_served
    TicketState.NO_SHOW -> R.string.history_status_no_show
    TicketState.CANCELLED -> R.string.history_status_cancelled
    else -> R.string.history_status_failed
}

private fun iconFor(status: String): Int = when (status) {
    TicketState.SERVED -> R.drawable.ic_check_circle
    TicketState.CANCELLED -> R.drawable.ic_x_circle
    else -> R.drawable.ic_info
}

private fun dateText(t: TicketEntity): String? {
    val millis = runCatching { Instant.parse(t.clientCreatedAt).toEpochMilli() }.getOrNull() ?: return null
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}
