package com.rabi.chess.engine

import kotlin.math.abs

/**
 * Stateless move generation and game-state queries over a [Board].
 *
 * Strategy: generate pseudo-legal moves, then keep only those that don't leave the
 * mover's own king in check (verified by make/undo). Castling is fully validated at
 * generation time (empty squares, king not in/through/into check).
 */
object MoveGenerator {

    private val KNIGHT_OFFSETS = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1
    )
    private val KING_OFFSETS = listOf(
        -1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1
    )
    private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

    /** All fully-legal moves for the side to move. */
    fun legalMoves(board: Board): List<Move> {
        val color = board.sideToMove
        val result = ArrayList<Move>(48)
        for (move in pseudoLegalMoves(board, color)) {
            board.makeMove(move)
            val kingPos = board.kingPosition(color)
            val inCheck = kingPos != null && isSquareAttacked(board, kingPos, color.opposite())
            board.undoMove()
            if (!inCheck) result.add(move)
        }
        return result
    }

    fun isInCheck(board: Board, color: PieceColor): Boolean {
        val kingPos = board.kingPosition(color) ?: return false
        return isSquareAttacked(board, kingPos, color.opposite())
    }

    fun result(board: Board): GameResult {
        if (legalMoves(board).isEmpty()) {
            return if (isInCheck(board, board.sideToMove)) GameResult.CHECKMATE
            else GameResult.STALEMATE
        }
        if (board.halfmoveClock >= 100) return GameResult.DRAW_FIFTY_MOVE
        if (isInsufficientMaterial(board)) return GameResult.DRAW_INSUFFICIENT_MATERIAL
        return GameResult.ONGOING
    }

    // ---- Pseudo-legal generation -----------------------------------------

    fun pseudoLegalMoves(board: Board, color: PieceColor): List<Move> {
        val moves = ArrayList<Move>(48)
        for ((pos, piece) in board.pieces()) {
            if (piece.color != color) continue
            when (piece.type) {
                PieceType.PAWN -> pawnMoves(board, pos, piece, moves)
                PieceType.KNIGHT -> stepMoves(board, pos, piece, KNIGHT_OFFSETS, moves)
                PieceType.KING -> {
                    stepMoves(board, pos, piece, KING_OFFSETS, moves)
                    castlingMoves(board, pos, piece, moves)
                }
                PieceType.BISHOP -> slideMoves(board, pos, piece, BISHOP_DIRS, moves)
                PieceType.ROOK -> slideMoves(board, pos, piece, ROOK_DIRS, moves)
                PieceType.QUEEN -> slideMoves(board, pos, piece, ROOK_DIRS + BISHOP_DIRS, moves)
            }
        }
        return moves
    }

    private fun stepMoves(
        board: Board, from: Position, piece: Piece,
        offsets: List<Pair<Int, Int>>, out: MutableList<Move>
    ) {
        for ((df, dr) in offsets) {
            val to = from.offset(df, dr)
            if (!to.isOnBoard) continue
            val occupant = board.pieceAt(to)
            if (occupant == null || occupant.color != piece.color) out.add(Move(from, to))
        }
    }

    private fun slideMoves(
        board: Board, from: Position, piece: Piece,
        dirs: List<Pair<Int, Int>>, out: MutableList<Move>
    ) {
        for ((df, dr) in dirs) {
            var to = from.offset(df, dr)
            while (to.isOnBoard) {
                val occupant = board.pieceAt(to)
                if (occupant == null) {
                    out.add(Move(from, to))
                } else {
                    if (occupant.color != piece.color) out.add(Move(from, to))
                    break
                }
                to = to.offset(df, dr)
            }
        }
    }

    private fun pawnMoves(board: Board, from: Position, piece: Piece, out: MutableList<Move>) {
        val dir = if (piece.color == PieceColor.WHITE) 1 else -1
        val startRank = if (piece.color == PieceColor.WHITE) 1 else 6
        val promoRank = if (piece.color == PieceColor.WHITE) 7 else 0

        // Forward one.
        val one = from.offset(0, dir)
        if (one.isOnBoard && board.pieceAt(one) == null) {
            addPawnAdvance(from, one, promoRank, out)
            // Forward two from the start rank.
            if (from.rank == startRank) {
                val two = from.offset(0, 2 * dir)
                if (board.pieceAt(two) == null) out.add(Move(from, two))
            }
        }

        // Captures (including en passant).
        for (df in listOf(-1, 1)) {
            val target = from.offset(df, dir)
            if (!target.isOnBoard) continue
            val occupant = board.pieceAt(target)
            if (occupant != null && occupant.color != piece.color) {
                addPawnAdvance(from, target, promoRank, out)
            } else if (occupant == null && target == board.enPassantTarget) {
                out.add(Move(from, target, isEnPassant = true))
            }
        }
    }

    private fun addPawnAdvance(from: Position, to: Position, promoRank: Int, out: MutableList<Move>) {
        if (to.rank == promoRank) {
            for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                out.add(Move(from, to, promotion = t))
            }
        } else {
            out.add(Move(from, to))
        }
    }

    private fun castlingMoves(board: Board, from: Position, king: Piece, out: MutableList<Move>) {
        val color = king.color
        val rank = if (color == PieceColor.WHITE) 0 else 7
        if (from != Position(4, rank)) return
        // Cannot castle out of check.
        if (isSquareAttacked(board, from, color.opposite())) return

        val rights = board.castling
        val kingSide = if (color == PieceColor.WHITE) rights.whiteKingSide else rights.blackKingSide
        val queenSide = if (color == PieceColor.WHITE) rights.whiteQueenSide else rights.blackQueenSide

        if (kingSide && isRook(board, 7, rank, color) &&
            board.pieceAt(5, rank) == null && board.pieceAt(6, rank) == null &&
            !isSquareAttacked(board, Position(5, rank), color.opposite()) &&
            !isSquareAttacked(board, Position(6, rank), color.opposite())
        ) {
            out.add(Move(from, Position(6, rank), isCastle = true))
        }
        if (queenSide && isRook(board, 0, rank, color) &&
            board.pieceAt(1, rank) == null && board.pieceAt(2, rank) == null &&
            board.pieceAt(3, rank) == null &&
            !isSquareAttacked(board, Position(3, rank), color.opposite()) &&
            !isSquareAttacked(board, Position(2, rank), color.opposite())
        ) {
            out.add(Move(from, Position(2, rank), isCastle = true))
        }
    }

    private fun isRook(board: Board, file: Int, rank: Int, color: PieceColor): Boolean {
        val p = board.pieceAt(file, rank)
        return p != null && p.type == PieceType.ROOK && p.color == color
    }

    // ---- Attack detection -------------------------------------------------

    /** True if [by] attacks [target] (ignores pins; used for check/castle tests). */
    fun isSquareAttacked(board: Board, target: Position, by: PieceColor): Boolean {
        // Pawn attacks: a [by] pawn sits one rank "behind" the target from its perspective.
        val pawnDir = if (by == PieceColor.WHITE) 1 else -1
        for (df in listOf(-1, 1)) {
            val src = Position(target.file + df, target.rank - pawnDir)
            val p = board.pieceAt(src)
            if (p != null && p.color == by && p.type == PieceType.PAWN) return true
        }
        // Knights.
        for ((df, dr) in KNIGHT_OFFSETS) {
            val p = board.pieceAt(target.file + df, target.rank + dr)
            if (p != null && p.color == by && p.type == PieceType.KNIGHT) return true
        }
        // King.
        for ((df, dr) in KING_OFFSETS) {
            val p = board.pieceAt(target.file + df, target.rank + dr)
            if (p != null && p.color == by && p.type == PieceType.KING) return true
        }
        // Sliding: rook/queen orthogonally, bishop/queen diagonally.
        if (slidingHit(board, target, ROOK_DIRS, by, PieceType.ROOK)) return true
        if (slidingHit(board, target, BISHOP_DIRS, by, PieceType.BISHOP)) return true
        return false
    }

    private fun slidingHit(
        board: Board, target: Position, dirs: List<Pair<Int, Int>>,
        by: PieceColor, straight: PieceType
    ): Boolean {
        for ((df, dr) in dirs) {
            var pos = target.offset(df, dr)
            while (pos.isOnBoard) {
                val p = board.pieceAt(pos)
                if (p != null) {
                    if (p.color == by && (p.type == straight || p.type == PieceType.QUEEN)) return true
                    break
                }
                pos = pos.offset(df, dr)
            }
        }
        return false
    }

    // ---- Draw detection ---------------------------------------------------

    private fun isInsufficientMaterial(board: Board): Boolean {
        var minors = 0
        for ((_, piece) in board.pieces()) {
            when (piece.type) {
                PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN -> return false
                PieceType.BISHOP, PieceType.KNIGHT -> minors++
                PieceType.KING -> {}
            }
        }
        // K vs K, or K + single minor vs K — neither side can force mate.
        return minors <= 1
    }
}
