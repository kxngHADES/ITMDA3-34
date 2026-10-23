package com.itmda.swiftq.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTheme

@Composable
fun LeaveQueueScreen(
    ticketNumber: String,
    onConfirmLeave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.leave_queue_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.leave_queue_message, ticketNumber),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            )

            Button(onClick = onConfirmLeave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.leave_queue_confirm))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.leave_queue_stay))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LeaveQueueScreenPreview() {
    SwiftQTheme {
        LeaveQueueScreen(
            ticketNumber = "A-014",
            onConfirmLeave = {},
            onCancel = {},
        )
    }
}
