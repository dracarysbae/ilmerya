package com.ilmerya.server

import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.*
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

internal const val GAME_CENTER_MAX_AGE_MS = 5 * 60_000L
internal const val GAME_CENTER_FUTURE_SKEW_MS = 30_000L
internal data class VerifiedGameCenterPlayer(val id: String, val bundle: String, val name: String,
    val proofHash: String, val proofExpires: Long)
internal data class AppleCertificateResponse(val bytes: ByteArray, val cacheMillis: Long)
internal fun interface AppleCertificateTransport { fun fetch(uri: URI): AppleCertificateResponse }

/** No redirects, credentials, caller-selected ports, or unbounded certificate downloads. */
private class AppleCertificateHttpTransport : AppleCertificateTransport {
    override fun fetch(uri: URI): AppleCertificateResponse {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        try {
            if (connection.responseCode != 200) throw ApiProblem(503, "Game Center certificate unavailable")
            if (connection.contentLengthLong > 65_536) throw ApiProblem(401, "Invalid Game Center certificate")
            val deadline = System.nanoTime() + 5_000_000_000L
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    if (System.nanoTime() > deadline) throw ApiProblem(503, "Game Center certificate timeout")
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 65_536) throw ApiProblem(401, "Invalid Game Center certificate")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val cache = connection.getHeaderField("Cache-Control").orEmpty()
            val seconds = Regex("(?:^|,)\\s*max-age=(\\d+)", RegexOption.IGNORE_CASE)
                .find(cache)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val age = connection.getHeaderField("Age")?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val lifetime = if (Regex("(?:no-store|no-cache)", RegexOption.IGNORE_CASE).containsMatchIn(cache)) 0L
                else (seconds.coerceAtMost(3600) - age).coerceAtLeast(0) * 1000
            return AppleCertificateResponse(bytes, lifetime)
        } catch (problem: ApiProblem) { throw problem }
        catch (_: Exception) { throw ApiProblem(503, "Game Center certificate unavailable") }
        finally { connection.disconnect() }
    }
}

/** Production construction always uses the JDK's root store; there is no environment trust bypass. */
internal class GameCenterIdentity private constructor(
    private val bundleId: String,
    private val transport: AppleCertificateTransport,
    private val roots: () -> Set<TrustAnchor>
) {
    constructor(bundleId: String) : this(bundleId, AppleCertificateHttpTransport(), ::jdkRoots)

    companion object {
        // Test seams accept certificates, never an arbitrary "verification succeeded" callback.
        internal fun forTests(bundleId: String, transport: AppleCertificateTransport, roots: Set<TrustAnchor>) =
            GameCenterIdentity(bundleId, transport) { roots }
        internal fun forTests(bundleId: String, transport: AppleCertificateTransport) =
            GameCenterIdentity(bundleId, transport, ::jdkRoots)

        private fun jdkRoots(): Set<TrustAnchor> {
            val store = KeyStore.getInstance("JKS")
            Files.newInputStream(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts")).use {
                store.load(it, "changeit".toCharArray())
            }
            return store.aliases().toList().mapNotNull { store.getCertificate(it) as? X509Certificate }
                .map { TrustAnchor(it, null) }.toSet()
        }
    }

    private data class Cached(val certificates: List<X509Certificate>, val until: Long)
    private val cache = object : LinkedHashMap<URI, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<URI, Cached>?) = size > 32
    }
    private val trustAnchors by lazy(roots)

    fun verify(request: JSONObject, now: Long): VerifiedGameCenterPlayer {
        if (bundleId.isBlank()) throw ApiProblem(503, "Game Center is not configured")
        fun rejected(): Nothing = throw ApiProblem(401, "Invalid Game Center proof")
        val player = request.getString("teamPlayerID")
        val bundle = request.getString("bundleID")
        if (player.length !in 1..256 || player.any { it.isISOControl() } || bundle != bundleId) rejected()
        // JSON integer or decimal string preserves UInt64; floating point coercion is forbidden.
        val rawTime = request.get("timestamp")
        if (rawTime !is String && rawTime !is Long && rawTime !is Int && rawTime !is java.math.BigInteger) rejected()
        val timeString = rawTime.toString()
        if (!timeString.matches(Regex("[0-9]{1,20}"))) rejected()
        val unsigned = timeString.toULongOrNull() ?: rejected()
        if (unsigned > Long.MAX_VALUE.toULong()) rejected() // Cannot be a fresh epoch-millisecond timestamp.
        val timestamp = unsigned.toLong()
        if (timestamp < now - GAME_CENTER_MAX_AGE_MS || timestamp > now + GAME_CENTER_FUTURE_SKEW_MS) rejected()
        fun decode(field: String, min: Int, max: Int): ByteArray {
            val value = request.getString(field)
            if (value.length > (max + 2) / 3 * 4) rejected()
            val bytes = try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) { rejected() }
            if (bytes.size !in min..max) rejected()
            return bytes
        }
        val signature = decode("signature", 256, 1024)
        val salt = decode("salt", 1, 256)
        val uri = gameCenterCertificateUri(request.getString("publicKeyURL"))
        val leaf = trustedCertificate(uri, now)
        val payload = player.toByteArray(Charsets.UTF_8) + bundle.toByteArray(Charsets.UTF_8) +
            ByteBuffer.allocate(8).putLong(timestamp).array() + salt
        val valid = try { Signature.getInstance("SHA256withRSA").run {
            initVerify(leaf.publicKey); update(payload); verify(signature)
        } } catch (_: Exception) { false }
        if (!valid) rejected()
        val digest = MessageDigest.getInstance("SHA-256")
        // Hash canonical signed bytes, independent of URL, base64 spelling and unsigned display name.
        val hash = digest.digest(payload).joinToString("") { "%02x".format(it) }
        val name = request.optString("displayName", "").filterNot {
            it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
        }.trim().take(40).ifBlank { "Game Center" }
        return VerifiedGameCenterPlayer(player, bundle, name, hash, timestamp + GAME_CENTER_MAX_AGE_MS + 1)
    }

    @Synchronized private fun certificates(uri: URI, now: Long): List<X509Certificate> {
        cache[uri]?.takeIf { now < it.until }?.let { return it.certificates }
        val response = transport.fetch(uri)
        if (response.bytes.size !in 1..65_536) throw ApiProblem(401, "Invalid Game Center certificate")
        val parsed = try { CertificateFactory.getInstance("X.509").generateCertificates(response.bytes.inputStream())
            .map { it as X509Certificate } } catch (_: Exception) { throw ApiProblem(401, "Invalid Game Center certificate") }
        if (parsed.size !in 1..6) throw ApiProblem(401, "Invalid Game Center certificate")
        cache[uri] = Cached(parsed, minOf(now + response.cacheMillis.coerceIn(0, 3_600_000), parsed.minOf { it.notAfter.time }))
        return parsed
    }

    private fun trustedCertificate(uri: URI, now: Long): X509Certificate {
        try {
            val supplied = certificates(uri, now).toMutableList()
            val leaf = supplied.first()
            val key = leaf.publicKey as? RSAPublicKey ?: throw CertificateException()
            if (key.modulus.bitLength() !in 2048..8192 || leaf.basicConstraints >= 0 || leaf.keyUsage?.get(0) == false)
                throw CertificateException()
            val chain = mutableListOf(leaf)
            // Explicit path construction avoids the JVM's optional, unrestricted AIA network fetches.
            repeat(5) {
                val current = chain.last()
                current.checkValidity(Date(now))
                val anchor = trustAnchors.firstOrNull { root ->
                    current.issuerX500Principal == root.trustedCert.subjectX500Principal &&
                        runCatching { current.verify(root.trustedCert.publicKey) }.isSuccess
                }
                if (anchor != null) {
                    val path = CertificateFactory.getInstance("X.509").generateCertPath(chain)
                    val parameters = PKIXParameters(setOf(anchor)).apply {
                        date = Date(now)
                        isRevocationEnabled = false // No unbounded CRL/OCSP network requests.
                    }
                    CertPathValidator.getInstance("PKIX").validate(path, parameters)
                    return leaf
                }
                var issuer = supplied.firstOrNull { it.subjectX500Principal == current.issuerX500Principal && it !in chain }
                if (issuer == null) {
                    val issuerUri = certificateIssuerUri(current) ?: throw CertificateException()
                    supplied.addAll(certificates(issuerUri, now))
                    issuer = supplied.firstOrNull { it.subjectX500Principal == current.issuerX500Principal && it !in chain }
                }
                chain.add(issuer ?: throw CertificateException())
            }
            throw CertificateException()
        } catch (problem: ApiProblem) { throw problem }
        catch (_: Exception) { throw ApiProblem(401, "Untrusted Game Center certificate") }
    }
}

internal fun gameCenterCertificateUri(value: String): URI {
    val uri = try { URI(value) } catch (_: Exception) { throw ApiProblem(401, "Invalid Game Center certificate URL") }
    if (value.length > 256 || uri.scheme != "https" || uri.host !in setOf("static.gc.apple.com", "gc.apple.com") ||
        uri.rawAuthority != uri.host || uri.rawQuery != null || uri.rawFragment != null ||
        !uri.rawPath.orEmpty().matches(Regex("/public-key/gc-prod-[0-9]{1,8}\\.cer")))
        throw ApiProblem(401, "Invalid Game Center certificate URL")
    return uri
}

/** Parse only caIssuers AIA (DER), then allow known CA repositories, always over HTTPS. */
private fun certificateIssuerUri(certificate: X509Certificate): URI? {
    data class Der(val tag: Int, val bytes: ByteArray)
    fun read(bytes: ByteArray): List<Der> {
        var offset = 0
        return buildList {
            while (offset < bytes.size) {
                val tag = bytes[offset++].toInt() and 255
                var length = bytes[offset++].toInt() and 255
                if (length >= 128) {
                    val count = length and 127
                    require(count in 1..3)
                    length = 0
                    repeat(count) { length = (length shl 8) or (bytes[offset++].toInt() and 255) }
                }
                require(length <= bytes.size - offset)
                add(Der(tag, bytes.copyOfRange(offset, offset + length)))
                offset += length
            }
        }
    }
    val extension = certificate.getExtensionValue("1.3.6.1.5.5.7.1.1") ?: return null
    val wrapped = read(extension).single().also { require(it.tag == 4) }
    val sequence = read(wrapped.bytes).single().also { require(it.tag == 48) }
    val oid = byteArrayOf(43, 6, 1, 5, 5, 7, 48, 2)
    for (description in read(sequence.bytes)) {
        if (description.tag != 48) continue
        val fields = read(description.bytes)
        if (fields.size != 2 || fields[0].tag != 6 || !fields[0].bytes.contentEquals(oid) || fields[1].tag != 134) continue
        val uri = URI(String(fields[1].bytes, Charsets.US_ASCII))
        if (uri.scheme !in setOf("http", "https") || uri.host !in setOf("cacerts.digicert.com", "certs.apple.com") ||
            uri.rawAuthority != uri.host || uri.rawQuery != null || uri.rawFragment != null ||
            !uri.rawPath.orEmpty().matches(Regex("/[A-Za-z0-9_-]{1,160}\\.(?:crt|cer)"))) continue
        return URI("https", uri.host, uri.path, null)
    }
    return null
}
