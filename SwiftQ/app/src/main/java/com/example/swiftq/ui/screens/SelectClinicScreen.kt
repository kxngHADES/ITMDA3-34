package com.example.swiftq.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftq.R
import com.example.swiftq.ui.theme.SwiftQTheme

data class ClinicSummary(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val currentQueueLength: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectClinicScreen(
    clinics: List<ClinicSummary>,
    onClinicSelected: (ClinicSummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.select_clinic_title)) }) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()) {
            items(clinics) { clinic ->
                Card(
                    onClick = { onClinicSelected(clinic) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = clinic.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = stringResource(R.string.clinic_distance, clinic.distanceKm), style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = pluralStringResource(
                                R.plurals.people_in_queue,
                                clinic.currentQueueLength,
                                clinic.currentQueueLength,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectClinicScreenPreview() {
    SwiftQTheme {
        SelectClinicScreen(
            clinics = listOf(
                ClinicSummary("1", "Thohoyandou Clinic", 2.3, 12),
                ClinicSummary("2", "Sibasa Health Centre", 4.1, 5),
            ),
            onClinicSelected = {},
            onBack = {},
        )
    }
}
