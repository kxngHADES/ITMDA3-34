package com.itmda.swiftq.ui.queue

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.components.FlowScaffold
import com.itmda.swiftq.ui.components.StateMessage
import com.itmda.swiftq.ui.status.LeaveUi
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme
import java.text.DateFormat
import java.util.Date

// Figma status colours. P1's theme has no tokens for them yet.
private val ErrorRed = Color(0xFFD70015)
private val LiveGreen = Color(0xFF34C759)
private val ProgressInk = Color(0xFF3634A3)

private fun formatNumber(raw: String?): String? = com.itmda.swiftq.ui.status.formatTicketNumber(raw)
private fun spokenNumber(raw: String?): String? = com.itmda.swiftq.ui.status.spokenTicketNumber(raw)

/** Figma 12 · Live queue. */
@Composable
fun LiveQueueView(
    ui: LiveQueueUi,
    failed: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    val dark = colors.background.luminance() < 0.5f
    val progressInk = if (dark) Color(0xFFB9B7FF) else ProgressInk
    FlowScaffold(
        title = stringResource(R.string.live_title),
        onBack = onBack,
        modifier = modifier,
        subtitleContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(if (ui.live) LiveGreen else colors.tertiary, CircleShape))
                val name = ui.clinicName.orEmpty()
                Text(
                    text = stringResource(if (ui.live) R.string.live_subtitle else R.string.live_subtitle_offline, name),
                    style = SwiftQTextStyles.Body, color = colors.secondary,
                )
            }
        },
        bottom = {
            if (ui.updatedAtMillis != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.live_updated, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ui.updatedAtMillis))),
                        style = SwiftQTextStyles.Body, color = colors.tertiary,
                    )
                }
            }
        },
    ) {
        if (!ui.loaded) {
            if (failed) StateMessage(R.drawable.ic_cloud_off, stringResource(R.string.live_error_title), stringResource(R.string.live_error_body), actionLabel = stringResource(R.string.join_try_again), onAction = onRetry)
            return@FlowScaffold
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(progressInk.copy(alpha = if (dark) 0.22f else 0.08f)).padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.live_now_seen), style = SwiftQTextStyles.Headline, color = progressInk)
                Text(
                    text = formatNumber(ui.nowServing) ?: stringResource(R.string.live_nobody_yet),
                    style = SwiftQTextStyles.Title1.copy(fontSize = if (ui.nowServing != null) 44.sp else 28.sp, lineHeight = if (ui.nowServing != null) 52.sp else 34.sp),
                    color = progressInk,
                    modifier = Modifier.semantics { spokenNumber(ui.nowServing)?.let { contentDescription = it } },
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp).background(colors.surface, CircleShape)) {
                Icon(painterResource(R.drawable.ic_people), contentDescription = null, tint = progressInk, modifier = Modifier.size(34.dp))
            }
        }
        Text(
            text = stringResource(R.string.live_next_in_line),
            style = SwiftQTextStyles.Footnote.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.48.sp),
            color = colors.secondary,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.rows.forEach { QueueRowItem(it) }
        }
        if (ui.moreAfter > 0) {
            Text(
                text = stringResource(R.string.live_more_after, ui.moreAfter),
                style = SwiftQTextStyles.Body, color = colors.tertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        if (ui.rows.isEmpty()) {
            Text(stringResource(R.string.live_empty), style = SwiftQTextStyles.Body, color = colors.secondary)
        }
    }
}

@Composable
private fun QueueRowItem(row: QueueRow) {
    val colors = SwiftQTheme.colors
    val bg = if (row.isMe) colors.blue else colors.grouped
    val ink = if (row.isMe) Color.White else colors.label
    val spoken = stringResource(if (row.isMe) R.string.a11y_queue_row_me else R.string.a11y_queue_row, row.position, spokenNumber(row.number).orEmpty())
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(start = 12.dp, end = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).background(colors.surface, CircleShape)) {
            Text(row.position.toString(), style = SwiftQTextStyles.Headline, color = if (row.isMe) colors.blue else colors.secondary)
        }
        Text(formatNumber(row.number).orEmpty(), style = SwiftQTextStyles.Title3, color = ink, modifier = Modifier.weight(1f))
        if (row.isMe) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(painterResource(R.drawable.ic_tab_user), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.live_you), style = SwiftQTextStyles.Headline, color = Color.White)
            }
        }
    }
}

/** The 64dp-high action button the Figma confirmation screens use: an icon beside a label. */
@Composable
private fun ActionButton(label: String, iconRes: Int, onClick: () -> Unit, outlined: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = SwiftQTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val ink = if (outlined) ErrorRed else Color.White
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .then(if (outlined) Modifier.background(colors.background).border(1.5.dp, ErrorRed, shape) else Modifier.background(colors.blue))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = ink, modifier = Modifier.size(26.dp))
        Text(label, style = SwiftQTextStyles.Title3, color = ink)
    }
}

@Composable
private fun CenteredMessage(
    badgeColor: Color,
    icon: Int,
    iconTint: Color,
    title: String,
    titleStyle: androidx.compose.ui.text.TextStyle,
    body: String,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = SwiftQTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp).background(badgeColor, CircleShape)) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(64.dp))
        }
        Text(title, style = titleStyle, color = colors.label, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(body, style = SwiftQTextStyles.Body, color = colors.secondary, textAlign = TextAlign.Center)
        extra?.invoke()
    }
}

/** Figma 13 · Leave queue (confirm). */
@Composable
fun LeaveConfirmView(
    ticketNumber: String?,
    leave: LeaveUi,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
        Spacer(Modifier.weight(1f))
        CenteredMessage(
            badgeColor = ErrorRed.copy(alpha = 0.10f), icon = R.drawable.ic_x_circle, iconTint = ErrorRed,
            title = stringResource(R.string.leave_title), titleStyle = SwiftQTextStyles.LargeTitle,
            body = stringResource(R.string.leave_body),
            extra = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.status_your_number), style = SwiftQTextStyles.Title3, color = colors.secondary)
                    Text(
                        text = formatNumber(ticketNumber).orEmpty(),
                        style = SwiftQTextStyles.LargeTitle.copy(fontSize = 48.sp, lineHeight = 58.sp), color = colors.label,
                        modifier = Modifier.semantics { spokenNumber(ticketNumber)?.let { contentDescription = it } },
                    )
                }
                leave.error?.let {
                    Text(
                        text = stringResource(if (it == LeaveUi.Error.NoConnection) R.string.leave_queue_no_connection else R.string.leave_queue_try_again),
                        style = SwiftQTextStyles.Subhead, color = ErrorRed, textAlign = TextAlign.Center,
                    )
                }
            },
        )
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            ActionButton(stringResource(R.string.leave_stay), R.drawable.ic_check, onStay, outlined = false)
            ActionButton(stringResource(R.string.leave_yes), R.drawable.ic_x_circle, onLeave, outlined = true, enabled = !leave.leaving)
        }
    }
}

/** Figma 14 · Left queue. */
@Composable
fun LeftQueueView(ticketNumber: String?, onHome: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SwiftQTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
        Spacer(Modifier.weight(1f))
        CenteredMessage(
            badgeColor = colors.grouped, icon = R.drawable.ic_ticket_outline, iconTint = colors.secondary,
            title = stringResource(R.string.left_title), titleStyle = SwiftQTextStyles.Title1,
            body = stringResource(R.string.left_body, formatNumber(ticketNumber).orEmpty()),
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(bottom = 16.dp)) {
            val shape = RoundedCornerShape(14.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(colors.blue).clickable(role = Role.Button, onClick = onHome),
            ) {
                Icon(painterResource(R.drawable.ic_tab_home), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                Text(stringResource(R.string.left_home), style = SwiftQTextStyles.Title3, color = Color.White)
            }
        }
    }
}
