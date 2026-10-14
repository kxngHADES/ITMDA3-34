package com.itmda.swiftq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
fun ConfirmJoinScreen(
    clinicName: String,
    servicePointName: String,
    priority: PriorityLevel,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.confirm_title)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.label_clinic), style = MaterialTheme.typography.labelMedium)
                    Text(text = clinicName, style = MaterialTheme.typography.titleMedium)

                    Text(
                        text = stringResource(R.string.label_service_point),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(text = servicePointName, style = MaterialTheme.typography.titleMedium)

                    Text(
                        text = stringResource(R.string.label_priority),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(text = stringResource(priority.labelRes), style = MaterialTheme.typography.titleMedium)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.confirm_join_queue))
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmJoinScreenPreview() {
    SwiftQTheme {
        ConfirmJoinScreen(
            clinicName = "Thohoyandou Clinic",
            servicePointName = "General Consultation",
            priority = PriorityLevel.NORMAL,
            onConfirm = {},
            onBack = {},
        )
    }
}
