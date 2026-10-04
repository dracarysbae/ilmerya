package com.bloxtrix.hexdrop.competition

import com.bloxtrix.hexdrop.engine.*
import com.bloxtrix.hexdrop.model.*
import kotlin.random.Random

const val RULESET_VERSION = 1
const val MAX_RUN_EVENTS = 20000
const val DROP_SETTLE_MS = 180L
const val CYCLE_SETTLE_MS = 420L
data class RunEvent(val ms:Long,val action:String)
data class CompletedRun(val seed:Long,val events:List<RunEvent>)
private val ACTION = Regex("[DC][0-4]")
/** Server and app compile this exact engine. Client score is never accepted. */
object RunReplay {
    fun initial(bag:StoneBag)=GameState(current=bag.next(1),nextQueue=List(3){bag.next(1)},phase=GamePhase.Playing)

    /** One placement or Cycle, exactly as the game applies it. */
    fun apply(state:GameState,bag:StoneBag,action:String):GameState {
        require(state.phase==GamePhase.Playing) { "Run already finished" }
        require(action.matches(ACTION)) { "Invalid action" }
        val col=action[1].digitToInt()
        return if(action[0]=='D') {
            val drop=requireNotNull(dropPiece(state.grid,col,state.current)) { "Full column" }
            val next=resolveTurn(state.copy(currentCol=col),drop.grid,true)
            next.copy(current=state.nextQueue.first(),nextQueue=state.nextQueue.drop(1)+bag.next(next.level))
        } else {
            require(state.energy>=3) { "Not enough energy" }
            resolveTurn(state.copy(currentCol=col),requireNotNull(cycleColumn(state.grid,col)) { "Cannot cycle" },false)
        }
    }

    /** Rebuilds a seeded run, including the bag position, without timing checks. */
    fun replay(seed:Long,events:List<RunEvent>):Pair<GameState,StoneBag> {
        val bag=StoneBag(Random(seed)); var state=initial(bag)
        for(event in events) state=apply(state,bag,event.action)
        return state to bag
    }

    fun verify(seed:Long,events:List<RunEvent>,elapsed:Long):GameState {
        require(events.size in 1..MAX_RUN_EVENTS)
        val bag=StoneBag(Random(seed)); var state=initial(bag); var nextAllowed=0L
        for(event in events) {
            require(event.ms>=nextAllowed && event.ms<=elapsed && event.ms<=6*60*60*1000) { "Invalid timing" }
            state=apply(state,bag,event.action)
            nextAllowed=event.ms+if(event.action[0]=='D') DROP_SETTLE_MS else CYCLE_SETTLE_MS
        }
        require(state.phase==GamePhase.GameOver) { "Finish the run before submitting" }
        return state
    }
}
