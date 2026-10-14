package com.itmda.swiftq.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itmda.swiftq.R
import com.itmda.swiftq.ui.theme.SwiftQTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveQueueScreen(
    ticketNumber: String,
    peopleAhead: Int,
    totalInQueue: Int,
    estimatedWaitMinutes: Int,
    onLeaveQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.live_queue_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.live_queue_your_ticket), style = MaterialTheme.typography.bodyMedium)
                    Text(text = ticketNumber, style = MaterialTheme.typography.displaySmall)

                    LinearProgressIndicator(
                        progress = {
                            if (totalInQueue == 0) 0f else (totalInQueue - peopleAhead).toFloat() / totalInQueue
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )

                    Text(
                        text = pluralStringResource(R.plurals.people_ahead, peopleAhead, peopleAhead),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.estimated_wait, estimatedWaitMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                TextButton(onClick = onLeaveQueue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.live_queue_leave))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveQueueScreenPreview() {
    SwiftQTheme {
        LiveQueueScreen(
            ticketNumber = "A-014",
            peopleAhead = 4,
            totalInQueue = 20,
            estimatedWaitMinutes = 15,
            onLeaveQueue = {},
        )
    }
}
