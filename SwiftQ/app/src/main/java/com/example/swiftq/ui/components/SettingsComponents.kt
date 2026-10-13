package com.example.swiftq.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme

/** Shared frame of the Settings detail screens: Back, a title, an optional subtitle, then [content]. */
@Composable
fun SettingsDetailScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwiftQTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 34.dp),
    ) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        ) {
            Text(text = title, style = SwiftQTextStyles.Title1, color = colors.label)
            if (subtitle != null) {
                Text(text = subtitle, style = SwiftQTextStyles.Body, color = colors.secondary)
            }
        }
        content()
    }
}

/** A single-choice row: optional badge, label, and a radio mark. Put several in a `selectableGroup`. */
@Composable
fun SettingsOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val colors = SwiftQTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (selected) colors.blue50 else colors.background)
            .border(
                border = if (selected) BorderStroke(2.dp, colors.blue) else BorderStroke(1.5.dp, colors.separator),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (badge != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(if (selected) colors.blue else colors.grouped, RoundedCornerShape(14.dp)),
            ) {
                Text(
                    text = badge,
                    style = SwiftQTextStyles.Headline,
                    color = if (selected) Color.White else colors.secondary,
                    maxLines = 1,
                )
            }
        }
        Text(text = label, style = SwiftQTextStyles.Title3, color = colors.label, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(painter = painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = colors.blue)
        } else {
            Box(modifier = Modifier.size(28.dp).border(1.5.dp, colors.tertiary, CircleShape))
        }
    }
}

/** A single-choice tile for a row of short options. Put several in a `selectableGroup`. */
@Composable
fun SettingsChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwiftQTheme.colors
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.blue50 else colors.background)
            .border(
                border = if (selected) BorderStroke(2.dp, colors.blue) else BorderStroke(1.5.dp, colors.separator),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        content = content,
    )
}

/** Figma "Toggle". Purely visual: the row that holds it carries the click and the semantics. */
@Composable
fun SwiftQToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = SwiftQTheme.colors
    val knobOffset by animateDpAsState(if (checked) 25.dp else 3.dp, label = "knob")
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(width = 56.dp, height = 34.dp)
            .background(if (checked) colors.blue else colors.tertiary, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.roundToPx(), 0) }
                .size(28.dp)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.18f), offset = DpOffset(0.dp, 2.dp)),
                )
                .background(Color.White, CircleShape),
        )
    }
}
