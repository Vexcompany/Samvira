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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vexcompany.samvira.domain.auth.AuthError
import com.vexcompany.samvira.domain.org.MembershipState
import com.vexcompany.samvira.domain.org.Organization
import com.vexcompany.samvira.domain.org.OrganizationError
import com.vexcompany.samvira.domain.org.OrganizationSelection
import com.vexcompany.samvira.ui.theme.SamviraTheme
import com.vexcompany.samvira.ui.theme.Teal400
import java.text.DateFormat
import java.util.Date

/**
 * Foundation home screen: shows the SAMVIRA brand plus the status of the
 * installation identity, backend session (0.3), and organization membership
 * (0.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = uiState,
        onConnect = viewModel::connect,
        onSignOut = viewModel::signOut,
        onSelectOrganization = viewModel::selectOrganization,
        onClearSelection = viewModel::clearSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
    onSelectOrganization: (Organization) -> Unit,
    onClearSelection: () -> Unit,
) {
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
                text = "Milestones 0.3 & 0.4 — backend contract, session, organizations",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            FoundationStatusCard()

            IdentityCard(state = state)

            SessionCard(
                state = state,
                onConnect = onConnect,
                onSignOut = onSignOut,
            )

            OrganizationsCard(
                state = state,
                onSelectOrganization = onSelectOrganization,
                onClearSelection = onClearSelection,
            )

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
            StatusRow("Backend contract & session")
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

@Composable
private fun SessionCard(
    state: HomeUiState,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Backend session",
                style = MaterialTheme.typography.titleLarge,
            )
            when (val session = state.sessionState) {
                SessionUi.Disconnected -> {
                    Text(
                        text = "Not connected",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onConnect, enabled = !state.connecting) {
                        Text(if (state.connecting) "Connecting…" else "Register & sign in")
                    }
                }

                is SessionUi.Active -> {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Teal400,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Session expires ${formatEpoch(session.expiresAtEpochMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = onSignOut) {
                        Text("Sign out")
                    }
                }

                is SessionUi.Failed -> {
                    Text(
                        text = "Sign-in failed: ${reasonText(session.reason)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onConnect, enabled = !state.connecting) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationsCard(
    state: HomeUiState,
    onSelectOrganization: (Organization) -> Unit,
    onClearSelection: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Organizations",
                style = MaterialTheme.typography.titleLarge,
            )
            when {
                state.sessionState !is SessionUi.Active -> {
                    Text(
                        text = "Sign in to see your organizations.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.organizationsLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Loading…", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                state.organizationsError != null -> {
                    Text(
                        text = "Could not load organizations: ${orgErrorText(state.organizationsError)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.organizations.isEmpty() -> {
                    Text(
                        text = "No organizations yet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "An installation may belong to at most two organizations; " +
                            "membership is granted by the server.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    state.organizations.forEach { organization ->
                        OrganizationRow(
                            organization = organization,
                            selected = (state.selection as? OrganizationSelection.Selected)
                                ?.organization?.id == organization.id,
                            selecting = state.contextLoading,
                            onSelect = { onSelectOrganization(organization) },
                        )
                    }
                    if (state.selection is OrganizationSelection.Selected) {
                        TextButton(onClick = onClearSelection) {
                            Text("Clear selection")
                        }
                    }
                    if (state.contextError != null) {
                        Text(
                            text = "Could not open organization: ${orgErrorText(state.contextError)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationRow(
    organization: Organization,
    selected: Boolean,
    selecting: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = organization.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = "State: ${membershipStateText(organization.state)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            selected -> Text(
                text = "Selected",
                style = MaterialTheme.typography.labelLarge,
                color = Teal400,
            )

            organization.state.isActive -> TextButton(
                onClick = onSelect,
                enabled = !selecting,
            ) {
                Text("Select")
            }

            else -> Text(
                text = "Unavailable",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun membershipStateText(state: MembershipState): String = when (state) {
    MembershipState.ACTIVE -> "active"
    MembershipState.PENDING -> "pending"
    MembershipState.REVOKED -> "revoked"
    MembershipState.SUSPENDED -> "suspended"
    MembershipState.UNKNOWN -> "unknown"
}

private fun orgErrorText(error: OrganizationError): String = when (error) {
    OrganizationError.NO_SESSION -> "no active session"
    OrganizationError.SESSION_REJECTED -> "session rejected by server"
    OrganizationError.NOT_A_MEMBER -> "not a member"
    OrganizationError.MEMBERSHIP_NOT_ACTIVE -> "membership not active"
    OrganizationError.UNKNOWN_ORGANIZATION -> "organization unknown"
    OrganizationError.ORG_CONTEXT_INVALID -> "organization context mismatch"
    OrganizationError.NETWORK -> "backend unreachable"
    OrganizationError.MALFORMED_RESPONSE -> "unexpected server response"
    OrganizationError.UNKNOWN -> "unknown error"
}

private fun reasonText(reason: AuthError): String = when (reason) {
    AuthError.KEYSTORE -> "secure key unavailable"
    AuthError.INSTALLATION_CONFLICT -> "installation conflict"
    AuthError.UNKNOWN_INSTALLATION -> "installation not recognized"
    AuthError.UNKNOWN_CHALLENGE -> "challenge not recognized"
    AuthError.CHALLENGE_EXPIRED -> "challenge expired"
    AuthError.CHALLENGE_REPLAYED -> "challenge already used"
    AuthError.CHALLENGE_MISMATCH -> "challenge mismatch"
    AuthError.INVALID_SIGNATURE -> "signature rejected"
    AuthError.MALFORMED_CHALLENGE -> "malformed challenge"
    AuthError.MALFORMED_REQUEST -> "request rejected by server"
    AuthError.MALFORMED_RESPONSE -> "unexpected server response"
    AuthError.SESSION_REJECTED -> "session rejected by server"
    AuthError.NETWORK -> "backend unreachable"
    AuthError.UNKNOWN -> "unknown error"
}

private fun formatEpoch(epochMs: Long): String =
    DateFormat.getDateTimeInstance().format(Date(epochMs))

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
                sessionState = SessionUi.Active(expiresAtEpochMs = System.currentTimeMillis() + 86_400_000L),
                organizations = listOf(
                    Organization("org-1", "Demo Organization", MembershipState.ACTIVE),
                ),
                selection = OrganizationSelection.Selected(
                    Organization("org-1", "Demo Organization", MembershipState.ACTIVE),
                ),
            ),
            onConnect = {},
            onSignOut = {},
            onSelectOrganization = {},
            onClearSelection = {},
        )
    }
}
