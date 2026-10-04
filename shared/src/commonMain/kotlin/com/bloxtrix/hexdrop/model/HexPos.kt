package com.bloxtrix.hexdrop.model

const val COLS = 5
const val ROWS = 7

data class HexPos(val row: Int, val col: Int) {
    fun isValid() = row in 0 until ROWS && col in 0 until COLS
}

fun HexPos.neighbors(): List<HexPos> {
    val (r, c) = this
    val diagonals = if (c % 2 == 0) {
        listOf(HexPos(r - 1, c - 1), HexPos(r, c - 1), HexPos(r - 1, c + 1), HexPos(r, c + 1))
    } else {
        listOf(HexPos(r, c - 1), HexPos(r + 1, c - 1), HexPos(r, c + 1), HexPos(r + 1, c + 1))
    }
    return (listOf(HexPos(r - 1, c), HexPos(r + 1, c)) + diagonals).filter { it.isValid() }
}
