package com.ilmerya.server

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.time.Duration

data class VerifiedPlayPlayer(val id: String,val name: String,val alternateId: String?=null)
internal fun interface GoogleTransport {
    fun request(url: String, form: String?, accessToken: String?): JSONObject
}
private class GoogleHttpTransport: GoogleTransport {
    private val client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    override fun request(url: String,form: String?,accessToken: String?): JSONObject {
        val builder=HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(8))
        if(accessToken!=null) builder.header("Authorization","Bearer $accessToken")
        if(form!=null) builder.header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)) else builder.GET()
        val response=try { client.send(builder.build(),HttpResponse.BodyHandlers.ofString()) }
            catch(_: Exception) { throw ApiProblem(503,"Google Play temporarily unavailable") }
        if(response.statusCode() !in 200..299) throw ApiProblem(if(response.statusCode()>=500) 503 else 401,"Google Play authentication failed")
        if(response.body().length>100000) throw ApiProblem(502,"Invalid Google response")
        return JSONObject(response.body())
    }
}

/** Auth codes and Google access tokens are used once in memory, never logged or persisted. */
internal class GooglePlayIdentity(
    private val appId: String,
    private val clientId: String,
    private val clientSecret: String,
    private val transport: GoogleTransport=GoogleHttpTransport()
) {
    fun verify(code: String): VerifiedPlayPlayer {
        if(!appId.matches(Regex("[0-9]+")) || clientId.isBlank() || clientSecret.isBlank())
            throw ApiProblem(503,"Play Games is not configured")
        require(code.length in 10..4096) { "Invalid authorization code" }
        fun enc(value: String)=URLEncoder.encode(value,Charsets.UTF_8)
        val form=mapOf("code" to code,"client_id" to clientId,"client_secret" to clientSecret,
            "grant_type" to "authorization_code","redirect_uri" to "").entries.joinToString("&") { enc(it.key)+"="+enc(it.value) }
        val token=transport.request("https://oauth2.googleapis.com/token",form,null).optString("access_token")
        if(token.isBlank()) throw ApiProblem(401,"Invalid Google token")
        val verification=transport.request("https://games.googleapis.com/games/v1/applications/$appId/verify",null,token)
        val id=verification.optString("player_id")
        val alternate=verification.optString("alternate_player_id").takeIf { it.isNotBlank() }
        val player=transport.request("https://games.googleapis.com/games/v1/players/me",null,token)
        if(id.isBlank() || id.length>256 || player.optString("playerId")!=id)
            throw ApiProblem(401,"Google player mismatch")
        val name=player.optString("displayName").filterNot { it.isISOControl() }.take(40).ifBlank { "Play Games" }
        return VerifiedPlayPlayer(id,name,alternate)
    }
}
