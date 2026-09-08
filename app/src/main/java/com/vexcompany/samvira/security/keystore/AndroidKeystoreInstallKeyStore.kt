package com.vexcompany.samvira.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Android Keystore-backed [InstallKeyStore].
 *
 * Security properties of the generated key:
 *  - EC P-256 (secp256r1) key pair generated inside the Android Keystore.
 *  - The private key is non-exportable by construction.
 *  - Purpose is limited to SIGN/VERIFY, digest to SHA-256.
 *  - A fixed alias is used, so re-provisioning after an app-data wipe reuses
 *    an existing Keystore entry instead of attempting to overwrite it.
 */
class AndroidKeystoreInstallKeyStore(
    private val alias: String = DEFAULT_ALIAS,
) : InstallKeyStore {

    override fun ensureKey(): String {
        val keyStore = keyStore()
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE,
            )
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generator.generateKeyPair()
        }
        return publicKey()
            ?: throw IllegalStateException(
                "Installation key exists but its public key could not be read.",
            )
    }

    override fun publicKey(): String? {
        val entry = keyStore().getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: return null
        val der = entry.certificate.publicKey.encoded ?: return null
        return pem(PUBLIC_KEY_HEADER, der)
    }

    override fun sign(data: ByteArray): ByteArray {
        val entry = keyStore().getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Installation key is not available.")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(entry.privateKey)
            update(data)
            sign()
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun pem(header: String, der: ByteArray): String = buildString {
        append("-----BEGIN ")
        append(header)
        append("-----\n")
        append(Base64.encodeToString(der, Base64.NO_WRAP))
        append("\n-----END ")
        append(header)
        append("-----\n")
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "samvira_installation_identity_v1"
        const val EC_CURVE = "secp256r1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val PUBLIC_KEY_HEADER = "PUBLIC KEY"
    }
}
