package com.ilmerya.server

import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.*

/** Local synthetic RSA/PKIX fixture; the signing key here has no production authority. */
internal object GameCenterFixture {
    const val bundle = "com.ozgames.ilmerya"
    const val url = "https://static.gc.apple.com/public-key/gc-prod-99.cer"
    fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/game-center/$name")).use { it.readBytes() }
    fun cert(name: String) = CertificateFactory.getInstance("X.509").generateCertificate(resource(name).inputStream()) as X509Certificate
    val leaf = cert("leaf.pem")
    val now = leaf.notBefore.time + 3_600_000L
    val roots = setOf(TrustAnchor(cert("root.pem"), null))
    fun verifier() = GameCenterIdentity.forTests(bundle, AppleCertificateTransport {
        AppleCertificateResponse(resource("leaf.pem"), 60_000)
    }, roots)
    fun proof(player: String = "T:fixture-player", timestamp: Long = now, saltValue: String = "random-fixture-salt"): JSONObject {
        val salt = saltValue.toByteArray()
        val payload = player.toByteArray() + bundle.toByteArray() + ByteBuffer.allocate(8).putLong(timestamp).array() + salt
        val pem = String(resource("signing-key.pem")).replace(Regex("-----[^-]+-----|\\s"), "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)))
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(key); update(payload); sign() }
        return JSONObject().put("teamPlayerID", player).put("bundleID", bundle).put("publicKeyURL", url)
            .put("timestamp", timestamp.toString()).put("salt", Base64.getEncoder().encodeToString(salt))
            .put("signature", Base64.getEncoder().encodeToString(signature)).put("displayName", "Player\n\u202e One")
    }
}

class GameCenterIdentityTest {
    private val f = GameCenterFixture
    private fun denied(block: () -> Unit) = assertEquals(401, assertFailsWith<ApiProblem>(block = block).status)

    @Test fun validSignedProofUsesTeamPlayerAndSanitizesUnsignedDisplayName() {
        val proof = f.proof()
        val result = f.verifier().verify(proof, f.now)
        assertEquals("T:fixture-player", result.id)
        assertEquals(f.bundle, result.bundle)
        assertEquals("Player One", result.name)
        assertEquals(64, result.proofHash.length)
        val renamed = f.verifier().verify(proof.put("displayName", "Renamed").put("timestamp", f.now), f.now)
        assertEquals(result.proofHash, renamed.proofHash)
        assertEquals("Renamed", renamed.name)
    }

    @Test fun tamperingSignaturePlayerSaltOrBundleFails() {
        val signature = Base64.getDecoder().decode(f.proof().getString("signature"))
        signature[0] = (signature[0].toInt() xor 1).toByte()
        val changes = listOf("signature" to Base64.getEncoder().encodeToString(signature),
            "teamPlayerID" to "T:other-player", "salt" to "YWJj", "bundleID" to "other.application")
        changes.forEach { (field, value) -> denied { f.verifier().verify(f.proof().put(field, value), f.now) } }
    }

    @Test fun strictFreshnessAndTimestampEncodingPrecedeNetwork() {
        var fetches = 0
        val verifier = GameCenterIdentity.forTests(f.bundle, AppleCertificateTransport { fetches++; error("Must not fetch") }, f.roots)
        val invalid = listOf<Any>(f.now - GAME_CENTER_MAX_AGE_MS - 1, f.now + GAME_CENTER_FUTURE_SKEW_MS + 1,
            "18446744073709551615", "18446744073709551616", "-1", "1e12", "1.5", 1.25)
        invalid.forEach { value -> denied { verifier.verify(f.proof().put("timestamp", value), f.now) } }
        assertEquals(0, fetches)
        f.verifier().verify(f.proof(timestamp = f.now - GAME_CENTER_MAX_AGE_MS), f.now)
        f.verifier().verify(f.proof(timestamp = f.now + GAME_CENTER_FUTURE_SKEW_MS), f.now)
    }

    @Test fun strictAppleUrlRejectsSsrfRedirectTargetsAndEncodingTricksBeforeFetch() {
        val bad = listOf("http://static.gc.apple.com/public-key/gc-prod-6.cer",
            "https://static.gc.apple.com.evil.test/public-key/gc-prod-6.cer",
            "https://evil.gc.apple.com/public-key/gc-prod-6.cer",
            "https://127.0.0.1/public-key/gc-prod-6.cer",
            "https://static.gc.apple.com:443/public-key/gc-prod-6.cer",
            "https://attacker@static.gc.apple.com/public-key/gc-prod-6.cer",
            "https://static.gc.apple.com/public-key/../gc-prod-6.cer",
            "https://static.gc.apple.com/public-key/%67c-prod-6.cer",
            f.url + "?next=http://127.0.0.1", f.url + "#ignored")
        var fetches = 0
        val verifier = GameCenterIdentity.forTests(f.bundle, AppleCertificateTransport { fetches++; error("Must not fetch") }, f.roots)
        bad.forEach { url -> denied { verifier.verify(f.proof().put("publicKeyURL", url), f.now) } }
        assertEquals(0, fetches)
    }

    @Test fun boundedCertificateCacheExpiresAndPkixRejectsUnknownRootAndExpiredLeaf() {
        var fetches = 0
        val transport = AppleCertificateTransport { fetches++; AppleCertificateResponse(f.resource("leaf.pem"), 1000) }
        val verifier = GameCenterIdentity.forTests(f.bundle, transport, f.roots)
        verifier.verify(f.proof(), f.now); verifier.verify(f.proof(), f.now + 999)
        assertEquals(1, fetches)
        verifier.verify(f.proof(), f.now + 1000)
        assertEquals(2, fetches)
        // Real JDK trust cannot be disabled by any request field or server environment option.
        denied { GameCenterIdentity.forTests(f.bundle, transport).verify(f.proof(), f.now) }
        val expiredTime = f.leaf.notAfter.time + 1
        denied { verifier.verify(f.proof(timestamp = expiredTime), expiredTime) }
    }

    @Test fun malformedOversizedCertificatesAndBase64AreRejected() {
        listOf(ByteArray(65_537), "not an X509 certificate".toByteArray()).forEach { bytes ->
            val verifier = GameCenterIdentity.forTests(f.bundle, AppleCertificateTransport { AppleCertificateResponse(bytes, 1000) }, f.roots)
            denied { verifier.verify(f.proof(), f.now) }
        }
        listOf("%%%", "A".repeat(2000), "").forEach { signature ->
            denied { f.verifier().verify(f.proof().put("signature", signature), f.now) }
        }
    }

    @Test fun missingBundleConfigurationDisablesAuthentication() {
        assertEquals(503, assertFailsWith<ApiProblem> { GameCenterIdentity("").verify(JSONObject(), f.now) }.status)
    }
}
