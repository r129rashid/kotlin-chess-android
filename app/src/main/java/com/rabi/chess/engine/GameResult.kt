package com.rabi.chess.engine

/** Outcome of the position for the side to move. */
enum class GameResult {
    ONGOING,
    CHECKMATE,        // side to move is checkmated (the other side won)
    STALEMATE,        // side to move has no legal move and is not in check
    DRAW_FIFTY_MOVE,
    DRAW_INSUFFICIENT_MATERIAL;

    val isGameOver: Boolean get() = this != ONGOING
    val isDraw: Boolean
        get() = this == STALEMATE || this == DRAW_FIFTY_MOVE || this == DRAW_INSUFFICIENT_MATERIAL
}
