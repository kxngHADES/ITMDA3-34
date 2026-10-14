package com.example.swiftq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme

/**
 * Frame shared by the join-flow screens, in the same style as the Settings detail screens:
 * Back, a title and subtitle, scrolling [content], and an optional pinned [bottom] action.
 */
@Composable
fun FlowScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwiftQTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        if (onBack != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(end = 12.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = colors.blue)
                Text(text = stringResource(R.string.action_back), style = SwiftQTextStyles.Title3, color = colors.blueText)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            ) {
                Text(text = title, style = SwiftQTextStyles.Title1, color = colors.label)
                if (subtitle != null) Text(text = subtitle, style = SwiftQTextStyles.Body, color = colors.secondary)
                subtitleContent?.invoke()
            }
            content()
        }
        if (bottom != null) Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp), content = bottom)
    }
}

/** Figma list card: a white badge with an icon, a title and a subtitle. Icon and label always together. */
@Composable
fun InfoCard(
    iconRes: Int,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailingChevron: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = SwiftQTheme.colors
    val shape = RoundedCornerShape(20.dp)
    val tint = if (enabled) colors.blue else colors.tertiary
    var m = modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp)
        .clip(shape)
        .background(colors.grouped)
    if (onClick != null) m = m.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    if (contentDescription != null) m = m.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = m.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).background(colors.surface, RoundedCornerShape(14.dp)),
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = SwiftQTextStyles.Headline, color = if (enabled) colors.label else colors.secondary)
            if (subtitle != null) Text(text = subtitle, style = SwiftQTextStyles.Subhead, color = colors.secondary)
        }
        if (trailingChevron) {
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.tertiary)
        }
    }
}

/** A calm notice (offline, saved place, errors): icon plus plain words, on the grouped background. */
@Composable
fun NoticeCard(iconRes: Int, text: String, modifier: Modifier = Modifier) {
    val colors = SwiftQTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.blue50)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = colors.blueText)
        Text(text = text, style = SwiftQTextStyles.Subhead, color = colors.label, modifier = Modifier.weight(1f))
    }
}

/** Centre-of-screen message for empty / offline / error states, with an optional action. */
@Composable
fun StateMessage(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SwiftQTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(40.dp))
        Text(text = title, style = SwiftQTextStyles.Title3, color = colors.label)
        Text(text = body, style = SwiftQTextStyles.Subhead, color = colors.secondary)
        if (actionLabel != null && onAction != null) {
            PrimaryButton(label = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
