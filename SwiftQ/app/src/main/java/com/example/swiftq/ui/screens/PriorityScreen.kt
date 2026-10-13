package com.example.swiftq.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTheme

enum class PriorityLevel(@StringRes val labelRes: Int) {
    NORMAL(R.string.priority_normal),
    ELDERLY(R.string.priority_elderly),
    PREGNANT(R.string.priority_pregnant),
    DISABILITY(R.string.priority_disability),
    EMERGENCY(R.string.priority_emergency),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityScreen(
    selectedPriority: PriorityLevel?,
    onPrioritySelected: (PriorityLevel) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.priority_title)) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(24.dp).fillMaxSize()) {
            Text(
                text = stringResource(R.string.priority_prompt),
                style = MaterialTheme.typography.titleMedium,
            )

            PriorityLevel.entries.forEach { priority ->
                val isSelected = priority == selectedPriority
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = isSelected, onClick = { onPrioritySelected(priority) })
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = isSelected, onClick = { onPrioritySelected(priority) })
                    Text(text = stringResource(priority.labelRes))
                }
            }

            Button(
                onClick = onContinue,
                enabled = selectedPriority != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PriorityScreenPreview() {
    SwiftQTheme {
        PriorityScreen(
            selectedPriority = PriorityLevel.NORMAL,
            onPrioritySelected = {},
            onContinue = {},
        )
    }
}
