package com.example.swiftq.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.swiftq.R
import com.example.swiftq.data.TextSize
import com.example.swiftq.data.ThemeMode
import com.example.swiftq.ui.components.SettingsChoiceTile
import com.example.swiftq.ui.components.SettingsDetailScreen
import com.example.swiftq.ui.components.SettingsOptionCard
import com.example.swiftq.ui.components.SwiftQToggle
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme

@get:StringRes
val TextSize.labelRes: Int
    get() = when (this) {
        TextSize.Normal -> R.string.text_size_normal
        TextSize.Large -> R.string.text_size_large
        TextSize.Largest -> R.string.text_size_largest
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.Light -> R.string.appearance_light
        ThemeMode.Dark -> R.string.appearance_dark
        ThemeMode.System -> R.string.appearance_system
    }

val warnBeforeMinuteOptions = listOf(5, 10, 15)

/** Settings / Language (Figma frame 18). Picking an option applies it straight away. */
@Composable
fun SettingsLanguageScreen(
    selectedLanguageCode: String,
    onLanguageSelected: (LanguageOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    languages: List<LanguageOption> = defaultLanguageOptions,
) {
    // The prompt is shown in the other language, so someone who cannot read this one can still find it.
    val otherCode = languages.firstOrNull { it.code != selectedLanguageCode }?.code
    val otherResources = if (otherCode != null) resourcesFor(otherCode) else LocalResources.current

    SettingsDetailScreen(
        title = stringResource(R.string.settings_language),
        subtitle = otherResources.getString(R.string.language_choose_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            languages.forEach { option ->
                SettingsOptionCard(
                    label = option.displayName,
                    badge = option.code.uppercase(),
                    selected = option.code == selectedLanguageCode,
                    onClick = { onLanguageSelected(option) },
                )
            }
        }
    }
}

/** Settings / Notifications (Figma frame 19). */
@Composable
fun SettingsNotificationsScreen(
    alertsEnabled: Boolean,
    onAlertsEnabledChange: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    vibrateEnabled: Boolean,
    onVibrateEnabledChange: (Boolean) -> Unit,
    warnMinutes: Int,
    onWarnMinutesChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    SettingsDetailScreen(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow(
                title = stringResource(R.string.notifications_alerts),
                description = stringResource(R.string.notifications_alerts_description),
                checked = alertsEnabled,
                onCheckedChange = onAlertsEnabledChange,
            )
            ToggleRow(
                title = stringResource(R.string.notifications_sound),
                description = stringResource(R.string.notifications_sound_description),
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
            )
            ToggleRow(
                title = stringResource(R.string.notifications_vibrate),
                description = stringResource(R.string.notifications_vibrate_description),
                checked = vibrateEnabled,
                onCheckedChange = onVibrateEnabledChange,
            )
        }
        Text(
            text = stringResource(R.string.notifications_warn_before),
            style = SwiftQTextStyles.Headline,
            color = colors.label,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
            warnBeforeMinuteOptions.forEach { minutes ->
                val selected = minutes == warnMinutes
                SettingsChoiceTile(
                    selected = selected,
                    onClick = { onWarnMinutesChange(minutes) },
                    modifier = Modifier.widthIn(min = 100.dp).heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.notifications_minutes, minutes),
                        style = SwiftQTextStyles.Title3,
                        color = if (selected) colors.blueText else colors.label,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.notifications_channel_note),
            style = SwiftQTextStyles.Body,
            color = colors.secondary,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = SwiftQTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.grouped)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(text = title, style = SwiftQTextStyles.Title3, color = colors.label)
            Text(text = description, style = SwiftQTextStyles.Body, color = colors.secondary)
        }
        SwiftQToggle(checked = checked)
    }
}

/** Settings / Text size (Figma frame 20). The whole app, this screen included, resizes on tap. */
@Composable
fun SettingsTextSizeScreen(
    selectedSize: TextSize,
    onSizeSelected: (TextSize) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    SettingsDetailScreen(
        title = stringResource(R.string.settings_text_size),
        subtitle = stringResource(R.string.text_size_description),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.grouped, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.text_size_preview_label),
                style = SwiftQTextStyles.Title3,
                color = colors.secondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.text_size_preview_ticket),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 66.sp),
                color = colors.blue,
                maxLines = 1,
            )
            Text(
                text = pluralStringResource(R.plurals.people_ahead, 3, 3),
                style = SwiftQTextStyles.Body,
                color = colors.label,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 24.dp).selectableGroup(),
        ) {
            TextSize.entries.forEach { size ->
                val selected = size == selectedSize
                SettingsChoiceTile(
                    selected = selected,
                    onClick = { onSizeSelected(size) },
                    cornerRadius = 16.dp,
                    modifier = Modifier.widthIn(min = 100.dp).heightIn(min = 112.dp),
                ) {
                    Text(
                        text = stringResource(R.string.text_size_sample_letter),
                        style = size.sampleStyle,
                        color = if (selected) colors.blue else colors.label,
                    )
                    Text(
                        text = stringResource(size.labelRes),
                        style = SwiftQTextStyles.Headline,
                        color = if (selected) colors.blueText else colors.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val TextSize.sampleStyle: TextStyle
    get() = when (this) {
        TextSize.Normal -> TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp)
        TextSize.Large -> TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp)
        TextSize.Largest -> TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp)
    }

/** Settings / Appearance. Not in the Figma file; it reuses the Language screen's option cards. */
@Composable
fun SettingsAppearanceScreen(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDetailScreen(
        title = stringResource(R.string.settings_appearance),
        subtitle = stringResource(R.string.appearance_description),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                SettingsOptionCard(
                    label = stringResource(mode.labelRes),
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
    }
}

@Preview(name = "18 · Settings / Language", widthDp = 393, heightDp = 852)
@Composable
private fun SettingsLanguageScreenPreview() {
    SwiftQTheme(darkTheme = false) {
        SettingsLanguageScreen(selectedLanguageCode = "en", onLanguageSelected = {}, onBack = {})
    }
}

@Preview(name = "19 · Settings / Notifications", widthDp = 393, heightDp = 852)
@Composable
private fun SettingsNotificationsScreenPreview() {
    SwiftQTheme(darkTheme = false) {
        SettingsNotificationsScreen(
            alertsEnabled = true,
            onAlertsEnabledChange = {},
            soundEnabled = true,
            onSoundEnabledChange = {},
            vibrateEnabled = true,
            onVibrateEnabledChange = {},
            warnMinutes = 5,
            onWarnMinutesChange = {},
            onBack = {},
        )
    }
}

@Preview(name = "20 · Settings / Text size", widthDp = 393, heightDp = 852)
@Composable
private fun SettingsTextSizeScreenPreview() {
    SwiftQTheme(darkTheme = false) {
        SettingsTextSizeScreen(selectedSize = TextSize.Normal, onSizeSelected = {}, onBack = {})
    }
}

@Preview(name = "Settings / Appearance (dark)", widthDp = 393, heightDp = 852)
@Composable
private fun SettingsAppearanceScreenPreview() {
    SwiftQTheme(darkTheme = true) {
        SettingsAppearanceScreen(selectedMode = ThemeMode.Dark, onModeSelected = {}, onBack = {})
    }
}
