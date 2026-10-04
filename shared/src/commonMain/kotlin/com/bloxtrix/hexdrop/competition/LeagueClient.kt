package com.bloxtrix.hexdrop.competition

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.*

data class RankedTicket(val id:String,val seed:Long,val week:String,val endsAt:Long,val playerId:String="",val startsAt:Long=0)
data class RankEntry(val player:String,val name:String,val rank:Int,val score:Long)
data class WeeklyBoard(val week:String,val endsAt:Long,val serverNow:Long,val entries:List<RankEntry>,val me:RankEntry?)
/** A closed week's final rank, kept by the server until the account is deleted. */
data class WeeklyFinish(val week:String,val rank:Int,val score:Long)
data class PlayerProfile(val id:String="",val name:String="",val history:List<WeeklyFinish> = emptyList(),val provider:String="")
data class WeeklyState(val profile:PlayerProfile=PlayerProfile(),val current:WeeklyBoard?=null,val previous:WeeklyBoard?=null,
    val loading:Boolean=false,val connected:Boolean=false,val error:String="",val signingIn:Boolean=false,val loginRequired:Boolean=true)
class LeagueFailure(val reason:String):Exception(reason)
interface LeagueClient {
    val state:StateFlow<WeeklyState>
    val configured:Boolean
    /** "play_games" or "game_center"; decides the sign-in wording. */
    val provider:String get() = "play_games"
    suspend fun refresh()
    suspend fun startRun():RankedTicket?
    suspend fun submit(ticket:RankedTicket,run:CompletedRun)
    suspend fun deleteAccount():Boolean
    /** Interactive sign-in started from the league screen. */
    suspend fun signIn() {}
}
object UnavailableLeague:LeagueClient {
    override val state=MutableStateFlow(WeeklyState())
    override val configured=false
    override suspend fun refresh() {}
    override suspend fun startRun():RankedTicket?=null
    override suspend fun submit(ticket:RankedTicket,run:CompletedRun) {}
    override suspend fun deleteAccount()=false
}
val LocalLeague=staticCompositionLocalOf<LeagueClient>{UnavailableLeague}
