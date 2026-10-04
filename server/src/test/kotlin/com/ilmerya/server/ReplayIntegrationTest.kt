package com.ilmerya.server

import com.bloxtrix.hexdrop.competition.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random
import kotlin.test.*

class ReplayIntegrationTest {
    @Test fun ticketReplayRankingAndIdempotencyUseRealIlmeryaRules() = WeeklyStore(":memory:").use { store ->
        val now=java.time.Instant.parse("2026-10-03T10:00:00Z").toEpochMilli()
        val player=store.register(now).getString("id")
        val ticket=store.startRun(player,now)
        val seed=ticket.getString("seed").toLong(); val bag=StoneBag(Random(seed))
        var state=RunReplay.initial(bag); val random=Random(73); val events=mutableListOf<RunEvent>()
        while(state.phase==GamePhase.Playing && events.size<5000) {
            val columns=(0 until COLS).filter{!state.grid.isColumnFull(it)}
            val drop=columns.isNotEmpty()
            val col=if(drop) columns.random(random) else (0 until COLS).first{cycleColumn(state.grid,it)!=null}
            events.add(RunEvent(events.size*500L,if(drop) "D$col" else "C$col"))
            val next=resolveTurn(state.copy(currentCol=col),if(drop) dropPiece(state.grid,col,state.current)!!.grid else cycleColumn(state.grid,col)!!,drop)
            state=if(drop) next.copy(current=state.nextQueue.first(),nextQueue=state.nextQueue.drop(1)+bag.next(next.level)) else next
        }
        assertEquals(GamePhase.GameOver,state.phase)
        val elapsed=events.size*500L
        val verified=RunReplay.verify(seed,events,elapsed)
        val id=ticket.getString("id")
        assertEquals(state.score,store.submit(id,player,"same-payload",verified.score,now+elapsed).getLong("score"))
        assertEquals(state.score,store.leaderboard(player,now+elapsed).getJSONObject("me").getLong("score"))
        assertTrue(store.submit(id,player,"same-payload",0,now+elapsed).getBoolean("duplicate"))
        assertEquals(409,assertFailsWith<ApiProblem>{store.submit(id,player,"altered",999999,now+elapsed)}.status)
    }
}
