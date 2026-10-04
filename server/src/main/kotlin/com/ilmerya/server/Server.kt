package com.ilmerya.server

import com.bloxtrix.hexdrop.competition.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.*
import java.net.InetSocketAddress
import java.nio.file.*
import java.util.concurrent.*

private class RateLimit {
    private val entries=object: LinkedHashMap<String,Pair<Long,Int>>(1024,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Pair<Long,Int>>?)=size>20000
    }
    @Synchronized fun allow(key: String,now: Long,limit: Int): Boolean {
        val old=entries[key]
        val count=if(old!=null && now-old.first<60000) old.second+1 else 1
        entries[key]=(if(old!=null && now-old.first<60000) old.first else now) to count
        return count<=limit
    }
}

fun main() {
    val running=startServer(System.getenv())
    Runtime.getRuntime().addShutdownHook(Thread { running.close() })
    println("ILMERYA weekly service listening on ${running.port}")
}

/** A started service; [close] stops HTTP, the settlement clock, workers and the database. */
class RunningServer(val port: Int, private val stop: () -> Unit): AutoCloseable { override fun close() = stop() }

fun startServer(env: Map<String,String>): RunningServer {
    val path=env["ILMERYA_DB"] ?: "data/ilmerya.sqlite"
    val jdbcUrl=env["ILMERYA_JDBC_URL"]?.takeIf { it.isNotBlank() }
    check(env["REQUIRE_PERSISTENT_DB"]!="true" || jdbcUrl!=null) { "Persistent PostgreSQL configuration is required" }
    if(jdbcUrl==null) Paths.get(path).toAbsolutePath().parent?.let(Files::createDirectories)
    val store=WeeklyStore(path,jdbcUrl)
    val google=GooglePlayIdentity(env["PGS_APP_ID"] ?: "",env["PGS_WEB_CLIENT_ID"] ?: "",
        env["PGS_WEB_CLIENT_SECRET"] ?: "")
    val gameCenter=GameCenterIdentity(env["APPLE_BUNDLE_ID"] ?: "")
    val allowGuests=env["ALLOW_GUEST_ACCOUNTS"]=="true"
    val scheduler=Executors.newSingleThreadScheduledExecutor()
    val settlement=SettlementClock(store::closeExpired)
    scheduler.scheduleAtFixedRate({ try { settlement.tick(System.currentTimeMillis()) } catch(e: Exception) { System.err.println("Weekly settlement failed: ${e.javaClass.simpleName}") } },0,1,TimeUnit.SECONDS)
    val port=(env["PORT"] ?: "8080").toInt()
    val server=HttpServer.create(InetSocketAddress(env["BIND_ADDRESS"] ?: "127.0.0.1",port),64)
    val pool=ThreadPoolExecutor(4,8,30,TimeUnit.SECONDS,ArrayBlockingQueue(128))
    server.executor=pool
    val limiter=RateLimit()
    // Enable only behind our reverse proxy; the container port must not be public.
    val trustProxy=env["TRUST_PROXY"]=="true"
    server.createContext("/") { exchange ->
        try {
            val now=System.currentTimeMillis()
            val pathName=exchange.requestURI.path
            val publicFile=when(pathName) { "/"->"index.html"; "/privacy"->"privacy.html"; "/delete-account"->"delete-account.html"
                "/support"->"support.html"; "/app-ads.txt"->"app-ads.txt"; else->null }
            if(publicFile!=null && (exchange.requestMethod=="GET" || exchange.requestMethod=="HEAD")) {
                val bytes=object {}.javaClass.getResourceAsStream("/public/$publicFile")?.use { it.readBytes() }
                    ?: throw ApiProblem(503,"Page unavailable")
                exchange.responseHeaders.set("Content-Type",if(publicFile.endsWith(".txt")) "text/plain; charset=utf-8" else "text/html; charset=utf-8")
                exchange.responseHeaders.set("Cache-Control","public, max-age=3600")
                exchange.responseHeaders.set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                exchange.responseHeaders.set("X-Content-Type-Options","nosniff")
                if(exchange.requestMethod=="HEAD") { exchange.sendResponseHeaders(200,-1); return@createContext }
                exchange.sendResponseHeaders(200,bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                return@createContext
            }
            if(pathName=="/health" && exchange.requestMethod=="GET") {
                exchange.reply(200,JSONObject().put("ok",true).put("game","ilmerya").put("ruleset",RULESET_VERSION))
                return@createContext
            }
            val remote=if(trustProxy) exchange.requestHeaders.getFirst("X-Forwarded-For")?.substringAfterLast(',')?.trim()
                ?.takeIf { it.length in 3..45 && it.all { c -> c.isDigit() || c in ".:abcdefABCDEF" } }
                ?: exchange.remoteAddress.address.hostAddress else exchange.remoteAddress.address.hostAddress
            if(!limiter.allow(remote,now,180)) throw ApiProblem(429,"Please wait")
            val body=if(exchange.requestMethod=="POST") exchange.requestBody.use { stream ->
                val bytes=stream.readNBytes(1_500_001)
                if(bytes.size>1_500_000) throw ApiProblem(413,"Request too large")
                String(bytes,Charsets.UTF_8).ifBlank { "{}" }
            } else "{}"
            if(pathName=="/v1/guest" && exchange.requestMethod=="POST") {
                if(!allowGuests) throw ApiProblem(403,"Use Google Play Games")
                if(!limiter.allow("guest:$remote",now,5)) throw ApiProblem(429,"Please wait")
                exchange.reply(201,store.register(now)); return@createContext
            }
            if(pathName=="/v1/auth/play-games" && exchange.requestMethod=="POST") {
                if(!limiter.allow("google:$remote",now,10)) throw ApiProblem(429,"Please wait")
                val identity=google.verify(JSONObject(body).getString("code"))
                exchange.reply(200,store.loginGoogle(identity,now)); return@createContext
            }
            if(pathName=="/v1/auth/game-center" && exchange.requestMethod=="POST") {
                if(!limiter.allow("apple:$remote",now,10)) throw ApiProblem(429,"Please wait")
                val identity=gameCenter.verify(JSONObject(body),now)
                exchange.reply(200,store.loginGameCenter(identity,System.currentTimeMillis())); return@createContext
            }
            val token=exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ") ?: throw ApiProblem(401,"Sign in required")
            if(token.length !in 32..128) throw ApiProblem(401,"Invalid session")
            val player=store.authenticate(token,now)
            if(!allowGuests && !store.isVerifiedPlayer(player)) throw ApiProblem(403,"Use Google Play Games or Game Center")
            val request=JSONObject(body)
            val response=when {
                pathName=="/v1/profile" && exchange.requestMethod=="GET" -> store.profile(player,now)
                pathName=="/v1/account/delete" && exchange.requestMethod=="POST" -> {
                    require(request.optBoolean("confirm",false)) { "Confirmation required" }
                    store.deleteAccount(player)
                }
                pathName=="/v1/leaderboard" && exchange.requestMethod=="GET" -> store.leaderboard(player,now,exchange.requestURI.rawQuery=="previous=true")
                pathName=="/v1/runs" && exchange.requestMethod=="POST" -> {
                    if(!limiter.allow("run:$player",now,8)) throw ApiProblem(429,"Please wait")
                    require(request.getInt("ruleset")==RULESET_VERSION) { "Update required" }
                    store.startRun(player,now)
                }
                pathName.matches(Regex("/v1/runs/[a-f0-9-]{36}/finish")) && exchange.requestMethod=="POST" -> {
                    if(!limiter.allow("finish:$player",now,4)) throw ApiProblem(429,"Please wait")
                    require(request.getInt("ruleset")==RULESET_VERSION) { "Update required" }
                    val id=pathName.split('/')[3]
                    val ticket=store.ticket(id,player)
                    val hash=sha256(body)
                    if(ticket.hash!=null) store.submit(id,player,hash,0,now) else {
                        if(now>=ticket.end) throw ApiProblem(409,"Week or run closed")
                        val array=request.getJSONArray("events")
                        require(array.length() in 1..MAX_RUN_EVENTS)
                        val events=List(array.length()) { i -> val e=array.getJSONArray(i); RunEvent(e.getLong(0),e.getString(1)) }
                        val result=RunReplay.verify(ticket.seed,events,now-ticket.started)
                        store.submit(id,player,hash,result.score,System.currentTimeMillis())
                    }
                }
                else -> throw ApiProblem(404,"Not found")
            }
            exchange.reply(200,response)
        } catch(e: ApiProblem) { exchange.reply(e.status,JSONObject().put("error",e.message)) }
        catch(e: IllegalArgumentException) { exchange.reply(400,JSONObject().put("error",e.message ?: "Invalid run")) }
        catch(e: JSONException) { exchange.reply(400,JSONObject().put("error","Invalid request")) }
        catch(e: Exception) { System.err.println("Request failed: ${e.javaClass.simpleName}"); exchange.reply(500,JSONObject().put("error","Please retry")) }
        finally { exchange.close() }
    }
    server.start()
    return RunningServer(server.address.port) { server.stop(1); scheduler.shutdownNow(); pool.shutdownNow(); store.close() }
}

private fun HttpExchange.reply(status: Int,value: JSONObject) {
    val bytes=value.toString().toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type","application/json; charset=utf-8")
    responseHeaders.set("Cache-Control","no-store")
    responseHeaders.set("X-Content-Type-Options","nosniff")
    sendResponseHeaders(status,bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
