package com.itmda.swiftq.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTheme

enum class SwiftQTab(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    Home(R.string.tab_home, R.drawable.ic_tab_home),
    Profile(R.string.profile_title, R.drawable.ic_tab_user),
    Settings(R.string.settings_title, R.drawable.ic_tab_settings),
}

/** Figma "Tab bar / Simple" — patient app bottom navigation. */
@Composable
fun SwiftQTabBar(
    selectedTab: SwiftQTab,
    onTabSelected: (SwiftQTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(SwiftQTheme.colors.background)) {
        HorizontalDivider(thickness = 1.dp, color = SwiftQTheme.colors.separator)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .selectableGroup(),
        ) {
            SwiftQTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) SwiftQTheme.colors.blue50 else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) }),
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        tint = if (selected) SwiftQTheme.colors.blue else SwiftQTheme.colors.secondary,
                    )
                    Text(
                        text = stringResource(tab.labelRes),
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.sp),
                        color = if (selected) SwiftQTheme.colors.blueText else SwiftQTheme.colors.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 393)
@Composable
private fun SwiftQTabBarPreview() {
    SwiftQTheme { SwiftQTabBar(selectedTab = SwiftQTab.Home, onTabSelected = {}) }
}
