package com.ilmerya.server

import org.json.JSONObject
import kotlin.test.*

class GooglePlayIdentityTest {
    @Test fun codeExchangeVerifiesApplicationThenRetrievesPlayerFromGoogle() {
        val calls=mutableListOf<String>()
        val verifier=GooglePlayIdentity("12345","web.apps.googleusercontent.com","test-secret",GoogleTransport { url,form,token ->
            calls+=url
            when {
                url.endsWith("/token") -> {
                    assertNull(token); assertTrue(form!!.contains("client_id=web.apps.googleusercontent.com"))
                    assertTrue(form.contains("redirect_uri=")); JSONObject().put("access_token","verified-token")
                }
                url.endsWith("/12345/verify") -> {
                    assertEquals("verified-token",token); JSONObject().put("player_id","google-id").put("alternate_player_id","old-id")
                }
                url.endsWith("/players/me") -> JSONObject().put("playerId","google-id").put("displayName","Player\nOne")
                else -> error("Unexpected URL")
            }
        })
        assertEquals(VerifiedPlayPlayer("google-id","PlayerOne","old-id"),verifier.verify("single-use-code"))
        assertEquals(3,calls.size)
    }
    @Test fun mismatchedIdentityAndUnconfiguredServerAreRejected() {
        val mismatch=GooglePlayIdentity("12345","client","secret",GoogleTransport { url,_,_ -> when {
            url.endsWith("/token") -> JSONObject().put("access_token","token")
            url.endsWith("/verify") -> JSONObject().put("player_id","real-player")
            else -> JSONObject().put("playerId","different-player").put("displayName","Impersonator")
        } })
        assertEquals(401,assertFailsWith<ApiProblem> { mismatch.verify("auth-code-value") }.status)
        assertEquals(503,assertFailsWith<ApiProblem> { GooglePlayIdentity("","","").verify("auth-code-value") }.status)
    }
    @Test fun failedGoogleVerificationCannotCreateALocalIdentity() {
        var calls=0
        val verifier=GooglePlayIdentity("12345","client","secret",GoogleTransport { _,_,_ ->
            calls++; if(calls==1) JSONObject().put("access_token","token") else throw ApiProblem(401,"Wrong app")
        })
        assertEquals(401,assertFailsWith<ApiProblem> { verifier.verify("auth-code-value") }.status)
        assertEquals(2,calls)
    }
}
