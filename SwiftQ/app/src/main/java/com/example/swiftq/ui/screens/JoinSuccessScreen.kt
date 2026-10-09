package com.example.swiftq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTheme

@Composable
fun JoinSuccessScreen(
    ticketNumber: String,
    estimatedWaitMinutes: Int,
    onViewLiveQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.join_success_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.label_ticket_number),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(text = ticketNumber, style = MaterialTheme.typography.displayMedium)
            Text(
                text = stringResource(R.string.estimated_wait, estimatedWaitMinutes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            Button(onClick = onViewLiveQueue, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                Text(stringResource(R.string.join_success_view_live))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinSuccessScreenPreview() {
    SwiftQTheme {
        JoinSuccessScreen(
            ticketNumber = "A-014",
            estimatedWaitMinutes = 25,
            onViewLiveQueue = {},
        )
    }
}
