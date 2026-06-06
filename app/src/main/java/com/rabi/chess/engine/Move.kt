package com.rabi.chess.engine

/**
 * A single move. Flags capture special rules so [Board] can apply/undo them precisely.
 *
 * @param from origin square
 * @param to destination square
 * @param promotion piece type a pawn promotes to, or null
 * @param isEnPassant true if this pawn capture removes a pawn on an adjacent file
 * @param isCastle true if this is a king two-square castling move
 */
data class Move(
    val from: Position,
    val to: Position,
    val promotion: PieceType? = null,
    val isEnPassant: Boolean = false,
    val isCastle: Boolean = false
) {
    fun uci(): String =
        from.algebraic() + to.algebraic() + (promotion?.let {
            when (it) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
        } ?: "")
}
