package com.bloxtrix.hexdrop

import com.bloxtrix.hexdrop.competition.*
import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random
import kotlin.test.*

class LeagueReplayTest {
    private fun run(seed:Long):Pair<GameState,List<RunEvent>> {
        val random=Random(seed+1); val bag=StoneBag(Random(seed)); var s=RunReplay.initial(bag)
        val events=mutableListOf<RunEvent>();var time=0L
        while(s.phase==GamePhase.Playing && events.size<2000) {
            val open=(0 until COLS).filter { !s.grid.isColumnFull(it) }
            val col=if(open.isNotEmpty()) open.random(random) else (0 until COLS).first { cycleColumn(s.grid,it)!=null }
            val drop=open.isNotEmpty()
            events.add(RunEvent(time,if(drop) "D$col" else "C$col"));time+=if(drop) 180 else 420
            val next=resolveTurn(s.copy(currentCol=col),if(drop) dropPiece(s.grid,col,s.current)!!.grid else cycleColumn(s.grid,col)!!,drop)
            s=if(drop) next.copy(current=s.nextQueue.first(),nextQueue=s.nextQueue.drop(1)+bag.next(next.level)) else next
        }
        assertEquals(GamePhase.GameOver,s.phase)
        return s to events
    }
    @Test fun replayMatchesRealEngineAcrossSeeds() {
        repeat(20) { seed -> val (s,events)=run(seed.toLong()); val actual=RunReplay.verify(seed.toLong(),events,1_000_000)
            assertEquals(s.score,actual.score);assertEquals(s.grid,actual.grid);assertEquals(s.energy,actual.energy) }
    }
    @Test fun rejectsEarlySubmissionAndIllegalActions() {
        assertFailsWith<IllegalArgumentException> { RunReplay.verify(1,listOf(RunEvent(0,"D0")),1000) }
        assertFailsWith<IllegalArgumentException> { RunReplay.verify(1,listOf(RunEvent(0,"C0")),1000) }
        assertFailsWith<IllegalArgumentException> { RunReplay.verify(1,listOf(RunEvent(0,"D5")),1000) }
    }
    @Test fun rejectsFastForwardAndMovesAfterEnd() {
        val (_,events)=run(5)
        assertFailsWith<IllegalArgumentException> {RunReplay.verify(5,events.map{it.copy(ms=0)},1_000_000)}
        assertFailsWith<IllegalArgumentException> {RunReplay.verify(5,events,0)}
        assertFailsWith<IllegalArgumentException> {RunReplay.verify(5,events+RunEvent(999999,"D0"),1_000_000)}
    }
}
