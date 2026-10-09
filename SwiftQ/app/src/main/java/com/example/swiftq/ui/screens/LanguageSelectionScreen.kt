package com.example.swiftq.ui.screens

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.components.PrimaryButton
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme
import java.util.Locale

/** [displayName] and [subtitle] are written in the language itself, so they are never translated. */
data class LanguageOption(val code: String, val displayName: String, val subtitle: String)

val defaultLanguageOptions = listOf(
    LanguageOption("en", "English", "Continue in English"),
    LanguageOption("af", "Afrikaans", "Gaan voort in Afrikaans"),
)

@Composable
fun LanguageSelectionScreen(
    languages: List<LanguageOption> = defaultLanguageOptions,
    selectedLanguageCode: String?,
    onLanguageSelected: (LanguageOption) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The screen reads in whichever language is currently ticked, before it is applied app-wide.
    val selectedResources = resourcesFor(selectedLanguageCode)
    val otherResources = resourcesFor(languages.firstOrNull { it.code != selectedLanguageCode }?.code)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SwiftQTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, bottom = 24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
            // The heading is repeated in the other language so nobody is locked out by a wrong tap.
            Text(
                text = selectedResources.getString(R.string.language_choose_title),
                style = SwiftQTextStyles.Title1,
                color = SwiftQTheme.colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = otherResources.getString(R.string.language_choose_title),
                style = SwiftQTextStyles.Title3,
                color = SwiftQTheme.colors.blueText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = selectedResources.getString(R.string.language_description),
                style = SwiftQTextStyles.Subhead,
                color = SwiftQTheme.colors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 40.dp).selectableGroup(),
            ) {
                languages.forEach { option ->
                    LanguageOptionCard(
                        option = option,
                        selected = option.code == selectedLanguageCode,
                        onClick = { onLanguageSelected(option) },
                    )
                }
            }
        }

        PrimaryButton(
            label = selectedResources.getString(R.string.action_continue),
            onClick = onContinue,
            enabled = selectedLanguageCode != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = selectedResources.getString(R.string.language_change_later),
            style = SwiftQTextStyles.Footnote,
            color = SwiftQTheme.colors.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

/** Resources for [languageCode], or the app's current ones when it is null. */
@Composable
internal fun resourcesFor(languageCode: String?): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentResources = LocalResources.current
    return remember(context, configuration, currentResources, languageCode) {
        if (languageCode == null) {
            currentResources
        } else {
            val localized = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(languageCode)) }
            context.createConfigurationContext(localized).resources
        }
    }
}

@Composable
private fun LanguageOptionCard(
    option: LanguageOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SwiftQTheme.colors.blue50 else SwiftQTheme.colors.background)
            .border(
                border = if (selected) BorderStroke(2.dp, SwiftQTheme.colors.blue) else BorderStroke(1.dp, SwiftQTheme.colors.separator),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(18.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(if (selected) SwiftQTheme.colors.blue else SwiftQTheme.colors.grouped, RoundedCornerShape(12.dp)),
        ) {
            Text(
                text = option.code.uppercase(),
                style = SwiftQTextStyles.Headline,
                color = if (selected) Color.White else SwiftQTheme.colors.secondary,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(text = option.displayName, style = SwiftQTextStyles.Headline, color = SwiftQTheme.colors.label)
            Text(text = option.subtitle, style = SwiftQTextStyles.Footnote, color = SwiftQTheme.colors.secondary)
        }
        Image(
            painter = painterResource(
                if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
            ),
            contentDescription = null,
        )
    }
}

@Preview(name = "01 · Language selection", widthDp = 393, heightDp = 852)
@Composable
private fun LanguageSelectionScreenPreview() {
    SwiftQTheme {
        LanguageSelectionScreen(
            selectedLanguageCode = "en",
            onLanguageSelected = {},
            onContinue = {},
        )
    }
}
