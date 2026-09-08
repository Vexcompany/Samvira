package com.vexcompany.samvira.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vexcompany.samvira.ui.theme.SamviraTheme
import com.vexcompany.samvira.ui.theme.Teal400

/**
 * Foundation home screen: shows the SAMVIRA brand and the status of the
 * foundation subsystems established for Milestone 0.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state = uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(state: HomeUiState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SAMVIRA", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Privacy-first photo & memory viewer",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Milestone 0.1 — Foundation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            FoundationStatusCard()

            IdentityCard(state = state)

            Text(
                text = "View access is separate from download permission. " +
                    "Screenshot protection cannot stop external cameras or " +
                    "out-of-band capture.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FoundationStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Foundation status",
                style = MaterialTheme.typography.titleLarge,
            )
            StatusRow("Compose & Material 3 shell")
            StatusRow("Navigation host")
            StatusRow("Local persistence abstraction (DataStore)")
            StatusRow("Networking abstraction (OkHttp)")
            StatusRow("Installation identity (Android Keystore)")
        }
    }
}

@Composable
private fun StatusRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Teal400, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IdentityCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Installation identity",
                style = MaterialTheme.typography.titleLarge,
            )
            when {
                state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Provisioning…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                state.error != null -> {
                    Text(
                        text = "Identity unavailable: ${state.error}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    Text(
                        text = "Ready",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Teal400,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Installation ID: ${state.installationId.orEmpty().take(8)}…",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Key fingerprint: SHA-256 ${state.publicKeyFingerprint.orEmpty()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "The private key is generated in Android Keystore and " +
                            "never leaves the device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    SamviraTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                identityProvisioned = true,
                installationId = "3f2a9c1e-0000-0000-0000-000000000000",
                publicKeyFingerprint = "A1B2C3D4E5F60718",
            ),
        )
    }
}
