package com.simon.harmonichackernews.network

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.security.KeyChain
import android.security.KeyChainAliasCallback
import android.security.KeyChainException
import com.simon.harmonichackernews.R
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Opens Android's certificate chooser. Approving a certificate there is what grants Harmonic use
 * of its key, which stays in system storage; only the returned alias is ever saved.
 *
 * [onResult] runs on a binder thread and receives null when nothing was chosen. Android skips the
 * chooser entirely, with the same result, when no certificate is installed.
 */
internal fun chooseClientCertificate(
    activity: Activity,
    baseUrl: String,
    current: String?,
    onResult: (String?) -> Unit,
) {
    val url = baseUrl.toHttpUrlOrNull()
    try {
        KeyChain.choosePrivateKeyAlias(
            activity, KeyChainAliasCallback(onResult), null, null,
            url?.host, url?.port ?: -1, current?.ifEmpty { null },
        )
    } catch (_: ActivityNotFoundException) {
        onResult(null)
    }
}

/** Presents a certificate from Android's KeyChain on a transport that carries nothing else. */
internal class AndroidClientCertificateHttpClient(
    context: Context,
    private val userAgent: String,
) : ClientCertificateHttpClient {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var active: Pair<String, HttpClient>? = null

    override suspend fun client(certificate: String): HttpClient = mutex.withLock {
        active?.takeIf { it.first == certificate }?.second
            ?: withContext(Dispatchers.IO) { createClient(certificate) }.also { created ->
                // A changed certificate must not resume connections authenticated by the old one.
                active?.second?.close()
                active = certificate to created
            }
    }

    private fun createClient(alias: String): HttpClient {
        val keyManager = try {
            val key = KeyChain.getPrivateKey(appContext, alias)
            val chain = KeyChain.getCertificateChain(appContext, alias)
            if (key == null || chain.isNullOrEmpty()) null else KeyChainKeyManager(alias, key, chain)
        } catch (_: KeyChainException) {
            null
        } ?: throw CloudSummaryException(
            // Removed from the device, or the user revoked Harmonic's access to it.
            appContext.getString(R.string.client_certificate_unavailable, alias),
        )
        // The platform trust manager keeps server verification identical to every other request.
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS")
            .apply { init(arrayOf(keyManager), arrayOf(trustManager), null) }
        return createHarmonicHttpClient(
            createAndroidHttpEngine { sslSocketFactory(sslContext.socketFactory, trustManager) },
            userAgent,
        ) {
            // A redirect could lead to another host, which must never be offered the certificate.
            followRedirects = false
        }
    }
}

private class KeyChainKeyManager(
    private val alias: String,
    private val key: PrivateKey,
    private val chain: Array<X509Certificate>,
) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(
        keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?,
    ): String = alias

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?,
    ): String = alias

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
    override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain
    override fun getPrivateKey(alias: String?): PrivateKey = key

    override fun chooseServerAlias(
        keyType: String?, issuers: Array<out Principal>?, socket: Socket?,
    ): String? = null

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        null
}
