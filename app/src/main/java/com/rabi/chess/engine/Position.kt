package com.rabi.chess.engine

/**
 * A board square. [file] is the column 0..7 (a..h); [rank] is the row 0..7 where
 * rank 0 is White's back rank (chess rank 1) and rank 7 is Black's back rank (chess rank 8).
 */
data class Position(val file: Int, val rank: Int) {

    val isOnBoard: Boolean
        get() = file in 0..7 && rank in 0..7

    /** Algebraic name, e.g. "e4". */
    fun algebraic(): String = "${'a' + file}${rank + 1}"

    fun offset(df: Int, dr: Int): Position = Position(file + df, rank + dr)

    companion object {
        fun fromAlgebraic(s: String): Position {
            require(s.length == 2) { "Bad square: $s" }
            return Position(s[0] - 'a', s[1] - '1')
        }
    }
}
