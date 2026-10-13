package com.example.swiftq.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.BuildConfig
import com.example.swiftq.R
import com.example.swiftq.data.TextSize
import com.example.swiftq.data.ThemeMode
import com.example.swiftq.ui.components.SwiftQTab
import com.example.swiftq.ui.components.SwiftQTabBar
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme

/** Settings (Figma frame 17). Appearance is not in the design; it follows the same row pattern. */
@Composable
fun SettingsScreen(
    languageCode: String,
    notificationsEnabled: Boolean,
    textSize: TextSize,
    themeMode: ThemeMode,
    onLanguage: () -> Unit,
    onNotifications: () -> Unit,
    onTextSize: () -> Unit,
    onAppearance: () -> Unit,
    onPrivacy: () -> Unit,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwiftQTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = SwiftQTextStyles.Title1,
                color = colors.label,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRow(
                    title = stringResource(R.string.settings_language),
                    value = defaultLanguageOptions.firstOrNull { it.code == languageCode }?.displayName.orEmpty(),
                    onClick = onLanguage,
                ) { BadgeText(languageCode.uppercase()) }
                SettingsRow(
                    title = stringResource(R.string.settings_notifications),
                    value = stringResource(if (notificationsEnabled) R.string.settings_on else R.string.settings_off),
                    onClick = onNotifications,
                ) { BadgeIcon(R.drawable.ic_bell) }
                SettingsRow(
                    title = stringResource(R.string.settings_text_size),
                    value = stringResource(textSize.labelRes),
                    onClick = onTextSize,
                ) { BadgeText("Aa") }
                SettingsRow(
                    title = stringResource(R.string.settings_appearance),
                    value = stringResource(themeMode.labelRes),
                    onClick = onAppearance,
                ) { BadgeIcon(R.drawable.ic_moon) }
                SettingsRow(
                    title = stringResource(R.string.settings_privacy),
                    value = stringResource(R.string.settings_privacy_summary),
                    onClick = onPrivacy,
                ) { BadgeIcon(R.drawable.ic_check_circle) }
            }
            Spacer(modifier = Modifier.weight(1f).heightIn(min = 24.dp))
            Text(
                text = stringResource(
                    R.string.settings_version,
                    stringResource(R.string.app_name),
                    BuildConfig.VERSION_NAME,
                ),
                style = SwiftQTextStyles.Footnote,
                color = colors.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }

        SwiftQTabBar(
            selectedTab = SwiftQTab.Settings,
            onTabSelected = { tab ->
                when (tab) {
                    SwiftQTab.Home -> onHome()
                    SwiftQTab.Profile -> onProfile()
                    SwiftQTab.Settings -> Unit
                }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    badge: @Composable () -> Unit,
) {
    val colors = SwiftQTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.grouped)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).background(colors.surface, RoundedCornerShape(14.dp)),
        ) { badge() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(text = title, style = SwiftQTextStyles.Title3, color = colors.label)
            Text(text = value, style = SwiftQTextStyles.Body, color = colors.secondary)
        }
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.tertiary)
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(text = text, style = SwiftQTextStyles.Headline, color = SwiftQTheme.colors.blue, maxLines = 1)
}

@Composable
private fun BadgeIcon(iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = SwiftQTheme.colors.blue,
        modifier = Modifier.size(26.dp),
    )
}

@Preview(name = "17 · Settings", widthDp = 393, heightDp = 852)
@Composable
private fun SettingsScreenPreview() {
    SwiftQTheme(darkTheme = false) {
        SettingsScreen(
            languageCode = "en",
            notificationsEnabled = true,
            textSize = TextSize.Normal,
            themeMode = ThemeMode.Light,
            onLanguage = {},
            onNotifications = {},
            onTextSize = {},
            onAppearance = {},
            onPrivacy = {},
            onHome = {},
            onProfile = {},
        )
    }
}
