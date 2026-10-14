package com.itmda.swiftq.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    fullName: String,
    email: String,
    phoneNumber: String,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = fullName, style = MaterialTheme.typography.titleLarge)
                    Text(text = email, style = MaterialTheme.typography.bodyMedium)
                    Text(text = phoneNumber, style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(stringResource(R.string.profile_edit))
            }
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.action_log_out))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SwiftQTheme {
        ProfileScreen(
            fullName = "Thandi Mudau",
            email = "thandi@example.com",
            phoneNumber = "+27 82 123 4567",
            onEditProfile = {},
            onLogout = {},
        )
    }
}
