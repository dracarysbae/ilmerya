package com.ilmerya.server

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class WeekWindow(val id: String, val start: Long, val end: Long)
fun weekAt(ms: Long): WeekWindow {
    val zone = ZoneId.of("Europe/Istanbul")
    val monday = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone)
    return WeekWindow(monday.toLocalDate().toString(),monday.toInstant().toEpochMilli(),monday.plusWeeks(1).toInstant().toEpochMilli())
}

fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
data class RunTicket(val id: String,val player: String,val seed: Long,val started: Long,val end: Long,val week: String,val hash: String?)
class ApiProblem(val status: Int, message: String): RuntimeException(message)

/** Durable weekly results; closure and final standings commit together. */
class WeeklyStore(path: String, jdbcUrl: String? = null): AutoCloseable {
    private val database = LeagueDatabase(path, jdbcUrl)
    private val random = SecureRandom()
    init {
        database.transaction { database.connection { db -> db.createStatement().use { s ->
            s.execute("CREATE TABLE IF NOT EXISTS players(id TEXT PRIMARY KEY, token_hash TEXT UNIQUE NOT NULL, name TEXT NOT NULL, created BIGINT NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS weeks(id TEXT PRIMARY KEY, starts BIGINT NOT NULL, ends BIGINT NOT NULL, closed BIGINT)")
            s.execute("CREATE TABLE IF NOT EXISTS runs(id TEXT PRIMARY KEY, player TEXT NOT NULL REFERENCES players(id), seed BIGINT NOT NULL, started BIGINT NOT NULL, expires BIGINT NOT NULL, week TEXT NOT NULL REFERENCES weeks(id), payload_hash TEXT, score BIGINT, canceled BIGINT NOT NULL DEFAULT 0)")
            s.execute("CREATE INDEX IF NOT EXISTS runs_player ON runs(player)")
            s.execute("CREATE TABLE IF NOT EXISTS scores(week TEXT NOT NULL REFERENCES weeks(id), player TEXT NOT NULL REFERENCES players(id), score BIGINT NOT NULL, achieved BIGINT NOT NULL, run TEXT NOT NULL REFERENCES runs(id), PRIMARY KEY(week,player))")
            s.execute("CREATE INDEX IF NOT EXISTS scores_rank ON scores(week,score DESC,achieved,player)")
            s.execute("CREATE TABLE IF NOT EXISTS results(week TEXT NOT NULL, player TEXT NOT NULL, rank BIGINT NOT NULL, score BIGINT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(week,player))")
            s.execute("CREATE INDEX IF NOT EXISTS results_player ON results(player)")
            s.execute("CREATE TABLE IF NOT EXISTS google_identities(google_id TEXT PRIMARY KEY, player TEXT NOT NULL REFERENCES players(id))")
            s.execute("CREATE TABLE IF NOT EXISTS apple_identities(bundle_id TEXT NOT NULL, team_player_id TEXT NOT NULL, player TEXT NOT NULL REFERENCES players(id), PRIMARY KEY(bundle_id,team_player_id))")
            // Independent of account lifetime: deleting an account must not permit reuse of its proof.
            s.execute("CREATE TABLE IF NOT EXISTS apple_auth_proofs(hash TEXT PRIMARY KEY, expires BIGINT NOT NULL)")
            s.execute("CREATE INDEX IF NOT EXISTS apple_auth_proofs_expiry ON apple_auth_proofs(expires)")
            s.execute("CREATE TABLE IF NOT EXISTS sessions(hash TEXT PRIMARY KEY, player TEXT NOT NULL REFERENCES players(id), expires BIGINT NOT NULL)")
            s.execute("CREATE INDEX IF NOT EXISTS sessions_player ON sessions(player)")
        } } }
    }
    private fun execute(sql: String,vararg args: Any?) = database.connection { db -> db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i,v -> p.setObject(i+1,v) }; p.executeUpdate()
    } }
    private fun <T> query(sql: String,vararg args: Any?, read: (java.sql.ResultSet)->T): List<T> = database.connection { db -> db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i,v -> p.setObject(i+1,v) }
        p.executeQuery().use { rows -> buildList { while(rows.next()) add(read(rows)) } }
    } }
    private fun <T> transaction(block: ()->T): T = database.transaction(block)
    private fun ensureWeek(week: WeekWindow) {
        execute("INSERT INTO weeks(id,starts,ends) VALUES(?,?,?) ON CONFLICT DO NOTHING",week.id,week.start,week.end)
    }
    @Synchronized fun register(now: Long): JSONObject = transaction {
        val id=UUID.randomUUID().toString()
        val bytes=ByteArray(32).also(random::nextBytes)
        val token=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        execute("INSERT INTO players(id,token_hash,name,created) VALUES(?,?,?,?)",id,sha256(token),"Ilm-${id.take(6).uppercase()}",now)
        profileInternal(id).put("token",token)
    }
    @Synchronized fun loginGoogle(identity: VerifiedPlayPlayer,now: Long): JSONObject = transaction {
        val aliases=listOfNotNull(identity.id,identity.alternateId).distinct()
        val existing=aliases.flatMap { alias -> query("SELECT player FROM google_identities WHERE google_id=?",alias) { it.getString(1) } }.distinct()
        if(existing.size>1) throw ApiProblem(409,"Identity migration requires support")
        val id=existing.firstOrNull() ?: UUID.randomUUID().toString().also { fresh ->
            execute("INSERT INTO players(id,token_hash,name,created) VALUES(?,?,?,?)",fresh,sha256(UUID.randomUUID().toString()),identity.name,now)
        }
        aliases.forEach { execute("INSERT INTO google_identities VALUES(?,?) ON CONFLICT DO NOTHING",it,id) }
        execute("UPDATE players SET name=? WHERE id=?",identity.name,id)
        val token=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        execute("DELETE FROM sessions WHERE expires<=?",now)
        execute("INSERT INTO sessions VALUES(?,?,?)",sha256(token),id,now+30L*24*60*60*1000)
        // Keep at most 8 active devices per player.
        execute("DELETE FROM sessions WHERE player=? AND hash NOT IN (SELECT hash FROM sessions WHERE player=? ORDER BY expires DESC,hash DESC LIMIT 8)",id,id)
        profileInternal(id).put("token",token)
    }
    @Synchronized fun isGooglePlayer(player: String)=query("SELECT 1 FROM google_identities WHERE player=? LIMIT 1",player) { it.getInt(1) }.isNotEmpty()
    @Synchronized internal fun loginGameCenter(identity: VerifiedGameCenterPlayer, now: Long): JSONObject = transaction {
        if (now >= identity.proofExpires) throw ApiProblem(401,"Game Center proof expired")
        execute("DELETE FROM apple_auth_proofs WHERE expires<=?",now)
        if (execute("INSERT INTO apple_auth_proofs VALUES(?,?) ON CONFLICT DO NOTHING",identity.proofHash,identity.proofExpires)!=1)
            throw ApiProblem(409,"Game Center proof already used")
        val id=query("SELECT player FROM apple_identities WHERE bundle_id=? AND team_player_id=?",identity.bundle,identity.id) { it.getString(1) }
            .firstOrNull() ?: UUID.randomUUID().toString().also { fresh ->
                execute("INSERT INTO players(id,token_hash,name,created) VALUES(?,?,?,?)",fresh,sha256(UUID.randomUUID().toString()),identity.name,now)
                execute("INSERT INTO apple_identities VALUES(?,?,?)",identity.bundle,identity.id,fresh)
            }
        execute("UPDATE players SET name=? WHERE id=?",identity.name,id)
        val token=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        execute("DELETE FROM sessions WHERE expires<=?",now)
        execute("INSERT INTO sessions VALUES(?,?,?)",sha256(token),id,now+30L*24*60*60*1000)
        execute("DELETE FROM sessions WHERE player=? AND hash NOT IN (SELECT hash FROM sessions WHERE player=? ORDER BY expires DESC,hash DESC LIMIT 8)",id,id)
        profileInternal(id).put("token",token)
    }
    @Synchronized fun isGameCenterPlayer(player: String)=query("SELECT 1 FROM apple_identities WHERE player=? LIMIT 1",player) { it.getInt(1) }.isNotEmpty()
    @Synchronized fun isVerifiedPlayer(player: String)=isGooglePlayer(player) || isGameCenterPlayer(player)
    @Synchronized fun authenticate(token: String,now: Long=System.currentTimeMillis()): String =
        query("SELECT player FROM sessions WHERE hash=? AND expires>? UNION SELECT id FROM players WHERE token_hash=? AND id NOT IN (SELECT player FROM google_identities) AND id NOT IN (SELECT player FROM apple_identities)",sha256(token),now,sha256(token)) { it.getString(1) }
        .firstOrNull() ?: throw ApiProblem(401,"Session expired")

    private fun profileInternal(player: String): JSONObject {
        val profile=query("SELECT id,name FROM players WHERE id=?",player) {
            JSONObject().put("id",it.getString(1)).put("name",it.getString(2))
        }.single()
        profile.put("provider",when { isGooglePlayer(player) -> "play_games"; isGameCenterPlayer(player) -> "game_center"; else -> "guest" })
        profile.put("history",JSONArray(query("SELECT week,rank,score FROM results WHERE player=? ORDER BY week DESC LIMIT 12",player) {
            JSONObject().put("week",it.getString(1)).put("rank",it.getInt(2)).put("score",it.getLong(3))
        }))
        return profile
    }
    @Synchronized fun profile(player: String,now: Long): JSONObject { closeExpired(now); return profileInternal(player) }
    @Synchronized fun deleteAccount(player: String): JSONObject = transaction {
        // Remove dependants before their foreign-key parents. Other players' results stay final.
        for(table in listOf("sessions","google_identities","apple_identities","results","scores","runs"))
            execute("DELETE FROM $table WHERE player=?",player)
        execute("DELETE FROM players WHERE id=?",player)
        JSONObject().put("deleted",true)
    }
    @Synchronized fun startRun(player: String,now: Long): JSONObject {
        closeExpired(now)
        return transaction {
            val week=weekAt(now); ensureWeek(week)
            execute("UPDATE runs SET canceled=1 WHERE player=? AND payload_hash IS NULL",player)
            val id=UUID.randomUUID().toString(); val seed=random.nextLong()
            execute("INSERT INTO runs(id,player,seed,started,expires,week) VALUES(?,?,?,?,?,?)",id,player,seed,now,minOf(week.end,now+6*60*60*1000L),week.id)
            JSONObject().put("id",id).put("seed",seed.toString()).put("week",week.id).put("startsAt",now)
                .put("endsAt",week.end).put("ruleset",com.bloxtrix.hexdrop.competition.RULESET_VERSION)
        }
    }
    @Synchronized fun ticket(id: String,player: String): RunTicket = query("SELECT id,player,seed,started,expires,week,payload_hash,canceled FROM runs WHERE id=? AND player=?",id,player) {
        if(it.getInt(8)!=0) throw ApiProblem(409,"Run replaced")
        RunTicket(it.getString(1),it.getString(2),it.getLong(3),it.getLong(4),it.getLong(5),it.getString(6),it.getString(7))
    }.firstOrNull() ?: throw ApiProblem(404,"Run not found")

    @Synchronized fun submit(id: String,player: String,hash: String,score: Long,now: Long): JSONObject {
        closeExpired(now)
        return transaction {
            val ticket=ticket(id,player)
            if(ticket.hash!=null) {
                if(ticket.hash!=hash) throw ApiProblem(409,"Run already submitted")
                return@transaction JSONObject().put("accepted",true).put("duplicate",true)
            }
            if(now>=ticket.end || query("SELECT closed FROM weeks WHERE id=?",ticket.week) { it.getObject(1) }.first()!=null) throw ApiProblem(409,"Week or run closed")
            execute("UPDATE runs SET payload_hash=?,score=? WHERE id=?",hash,score,id)
            execute("INSERT INTO scores(week,player,score,achieved,run) VALUES(?,?,?,?,?) ON CONFLICT(week,player) DO UPDATE SET score=excluded.score,achieved=excluded.achieved,run=excluded.run WHERE excluded.score>scores.score",ticket.week,player,score,now,id)
            JSONObject().put("accepted",true).put("score",score).put("week",ticket.week)
        }
    }
    @Synchronized fun closeExpired(now: Long) = transaction {
        val expired=query("SELECT id FROM weeks WHERE ends<=? AND closed IS NULL ORDER BY starts",now) { it.getString(1) }
        for(week in expired) {
            val rows=query("SELECT s.player,s.score,p.name FROM scores s JOIN players p ON p.id=s.player WHERE s.week=? ORDER BY s.score DESC,s.achieved,s.player",week) {
                Triple(it.getString(1),it.getLong(2),it.getString(3))
            }
            rows.forEachIndexed { index,(player,score,name) ->
                val rank=index+1
                execute("INSERT INTO results VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",week,player,rank,score,name)
            }
            execute("UPDATE weeks SET closed=? WHERE id=? AND closed IS NULL",now,week)
        }
    }
    @Synchronized fun leaderboard(player: String,now: Long,previous: Boolean=false): JSONObject {
        val week=weekAt(if(previous) weekAt(now).start-1 else now)
        ensureWeek(week); closeExpired(now)
        val columns="player,score,rank,name"
        val ranking=if(previous) "SELECT player,score,rank,name FROM results WHERE week=?" else
            "SELECT s.player,s.score,ROW_NUMBER() OVER(ORDER BY s.score DESC,s.achieved,s.player) AS rank,p.name FROM scores s JOIN players p ON p.id=s.player WHERE week=?"
        fun row(r: java.sql.ResultSet)=JSONObject().put("playerId",r.getString(1)).put("score",r.getLong(2)).put("rank",r.getInt(3)).put("name",r.getString(4))
        val entries=query("SELECT $columns FROM ($ranking) ranked ORDER BY rank LIMIT 100",week.id,read=::row)
        val own=query("SELECT $columns FROM ($ranking) ranked WHERE player=?",week.id,player,read=::row).firstOrNull()
        return JSONObject().put("week",week.id).put("endsAt",week.end).put("serverNow",now).put("timeZone","Europe/Istanbul")
            .put("final",previous).put("entries",JSONArray(entries)).put("me",own ?: JSONObject.NULL)
    }
    @Synchronized override fun close() { database.close() }
}
