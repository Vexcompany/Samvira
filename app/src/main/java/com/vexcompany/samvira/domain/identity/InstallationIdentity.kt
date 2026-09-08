package com.vexcompany.samvira.domain.identity

/**
 * Installation-based identity.
 *
 * The private key material lives (and stays) inside Android Keystore and is
 * never exposed through this model. Only the public portion — the PEM public
 * key — together with a random installation identifier is surfaced. Per the
 * SAMVIRA security model:
 *
 *  - The identifier is random, not derived from IMEI/serial/MAC/advertising ID
 *    or any hardware fingerprint.
 *  - Uninstalling or factory-resetting creates a fresh identity; this model
 *    does not claim permanent physical-device uniqueness.
 */
data class InstallationIdentity(
    val installationId: String,
    val publicKeyPem: String,
    val createdAtEpochMs: Long,
)
