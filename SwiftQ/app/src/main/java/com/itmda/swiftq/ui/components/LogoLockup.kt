package com.itmda.swiftq.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTheme

/** Figma "Logo/Lockup" — app icon plus two-tone wordmark, for screen headers. */
@Composable
fun LogoLockup(modifier: Modifier = Modifier) {
    val appName = stringResource(R.string.app_name)
    // Artwork, so it keeps its designed size regardless of font scale.
    val wordmarkStyle = with(LocalDensity.current) {
        TextStyle(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.dp.toSp(),
            lineHeight = 34.dp.toSp(),
            letterSpacing = (-0.02).em,
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = appName },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(text = "Swift", color = SwiftQTheme.colors.label, style = wordmarkStyle, softWrap = false)
            Text(text = "Q", color = SwiftQTheme.colors.blue, style = wordmarkStyle, softWrap = false)
        }
    }
}
