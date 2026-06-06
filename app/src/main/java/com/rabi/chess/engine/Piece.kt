package com.rabi.chess.engine

/** The two sides. */
enum class PieceColor {
    WHITE, BLACK;

    fun opposite(): PieceColor = if (this == WHITE) BLACK else WHITE
}

/** The six piece kinds, with their standard material value (in pawns). */
enum class PieceType(val value: Int) {
    PAWN(100),
    KNIGHT(320),
    BISHOP(330),
    ROOK(500),
    QUEEN(900),
    KING(20000)
}

/** An immutable piece: a kind plus a color. */
data class Piece(val type: PieceType, val color: PieceColor) {

    /** Unicode glyph used by the renderer (solid glyphs; color is applied via Paint). */
    val glyph: String
        get() = when (type) {
            PieceType.KING -> "♚"
            PieceType.QUEEN -> "♛"
            PieceType.ROOK -> "♜"
            PieceType.BISHOP -> "♝"
            PieceType.KNIGHT -> "♞"
            PieceType.PAWN -> "♟"
        }
}
