package com.itmda.swiftq.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.components.LogoLockup
import com.itmda.swiftq.ui.components.SwiftQTab
import com.itmda.swiftq.ui.components.SwiftQTabBar
import com.itmda.swiftq.ui.theme.SwiftQTextStyles
import com.itmda.swiftq.ui.theme.SwiftQTheme

/** Home for a patient with no active ticket (Figma frame 02). */
@Composable
fun HomeScreen(
    onJoinQueue: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(SwiftQTheme.colors.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            LogoLockup(modifier = Modifier.align(Alignment.Start).padding(top = 4.dp))

            Spacer(modifier = Modifier.weight(1f))

            val cardShape = RoundedCornerShape(28.dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .dropShadow(
                        shape = cardShape,
                        shadow = Shadow(
                            radius = 24.dp,
                            spread = (-6).dp,
                            color = SwiftQTheme.colors.blue.copy(alpha = 0.28f),
                            offset = DpOffset(0.dp, 10.dp),
                        ),
                    )
                    .clip(cardShape)
                    .background(SwiftQTheme.colors.blue)
                    .clickable(role = Role.Button, onClick = onJoinQueue)
                    .padding(horizontal = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(104.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                ) {
                    Image(painter = painterResource(R.drawable.ic_ticket), contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.home_join_queue),
                    style = SwiftQTextStyles.LargeTitle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.home_no_account),
                style = SwiftQTextStyles.Body,
                color = SwiftQTheme.colors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        SwiftQTabBar(
            selectedTab = SwiftQTab.Home,
            onTabSelected = { tab ->
                when (tab) {
                    SwiftQTab.Home -> Unit
                    SwiftQTab.Profile -> onProfile()
                    SwiftQTab.Settings -> onSettings()
                }
            },
        )
    }
}

@Preview(name = "02 · Home (no active ticket)", widthDp = 393, heightDp = 852)
@Composable
private fun HomeScreenPreview() {
    SwiftQTheme {
        HomeScreen(onJoinQueue = {}, onProfile = {}, onSettings = {})
    }
}
