package com.example.swiftq.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTheme

@Composable
fun TurnAlarmScreen(
    ticketNumber: String,
    servicePointName: String,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.turn_alarm_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.turn_alarm_message, ticketNumber, servicePointName),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            )

            Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.turn_alarm_on_my_way))
            }
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.turn_alarm_snooze))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TurnAlarmScreenPreview() {
    SwiftQTheme {
        TurnAlarmScreen(
            ticketNumber = "A-014",
            servicePointName = "Room 3",
            onAcknowledge = {},
            onSnooze = {},
        )
    }
}
