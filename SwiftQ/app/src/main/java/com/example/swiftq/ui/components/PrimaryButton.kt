package com.example.swiftq.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.swiftq.ui.theme.SwiftQTextStyles
import com.example.swiftq.ui.theme.SwiftQTheme

/** Figma "Button" — Primary / Regular. One per screen. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SwiftQTheme.colors.blue, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 28.dp),
        modifier = modifier.defaultMinSize(minWidth = 160.dp).height(50.dp),
    ) {
        Text(text = label, style = SwiftQTextStyles.Headline)
    }
}
