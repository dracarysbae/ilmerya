package com.bloxtrix.hexdrop.model

sealed interface GamePhase {
    data object Playing  : GamePhase
    data object Paused   : GamePhase
    data object GameOver : GamePhase
}
