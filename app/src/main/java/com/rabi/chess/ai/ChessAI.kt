package com.rabi.chess.ai

import com.rabi.chess.engine.Board
import com.rabi.chess.engine.Move
import com.rabi.chess.engine.MoveGenerator
import com.rabi.chess.engine.PieceColor
import com.rabi.chess.engine.PieceType
import com.rabi.chess.engine.Position

/** Difficulty maps directly to minimax search depth. */
enum class Difficulty(val depth: Int) {
    EASY(2), MEDIUM(3), HARD(4)
}

/**
 * A self-contained chess engine opponent: alpha-beta minimax with a
 * material + piece-square-table evaluation. No external dependencies, runnable
 * off the main thread.
 */
object ChessAI {

    private const val MATE = 1_000_000
    private const val INF = 10_000_000

    /**
     * Best move for the side to move, searching to [difficulty] depth.
     * Returns null only if there are no legal moves.
     */
    fun bestMove(board: Board, difficulty: Difficulty): Move? {
        val mover = board.sideToMove
        val moves = orderMoves(board, MoveGenerator.legalMoves(board))
        if (moves.isEmpty()) return null

        var bestMove: Move? = null
        var bestScore = -INF
        var alpha = -INF
        val beta = INF

        for (move in moves) {
            board.makeMove(move)
            val score = -negamax(board, difficulty.depth - 1, -beta, -alpha, mover.opposite())
            board.undoMove()
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > alpha) alpha = score
        }
        return bestMove ?: moves.first()
    }

    /** Negamax with alpha-beta pruning. [perspective] is the side to move at this node. */
    private fun negamax(
        board: Board, depth: Int, alphaIn: Int, beta: Int, perspective: PieceColor
    ): Int {
        val moves = MoveGenerator.legalMoves(board)
        if (moves.isEmpty()) {
            // Checkmate (bad for the side to move) or stalemate (neutral).
            return if (MoveGenerator.isInCheck(board, board.sideToMove)) -MATE - depth else 0
        }
        if (depth == 0) return evaluate(board, perspective)

        var alpha = alphaIn
        var best = -INF
        for (move in orderMoves(board, moves)) {
            board.makeMove(move)
            val score = -negamax(board, depth - 1, -beta, -alpha, perspective.opposite())
            board.undoMove()
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break // prune
        }
        return best
    }

    /** Captures first — cheap move ordering that makes alpha-beta prune far more. */
    private fun orderMoves(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { move ->
            val victim = board.pieceAt(move.to)
            val capture = if (victim != null) victim.type.value else 0
            val promo = move.promotion?.value ?: 0
            capture + promo
        }

    /** Static evaluation in centipawns, from [perspective]'s point of view. */
    private fun evaluate(board: Board, perspective: PieceColor): Int {
        var score = 0
        for ((pos, piece) in board.pieces()) {
            val material = piece.type.value
            val positional = pieceSquareBonus(piece.type, piece.color, pos)
            val sign = if (piece.color == perspective) 1 else -1
            score += sign * (material + positional)
        }
        return score
    }

    // ---- Piece-square tables (from White's perspective; mirrored for Black) ----

    private fun pieceSquareBonus(type: PieceType, color: PieceColor, pos: Position): Int {
        val table = when (type) {
            PieceType.PAWN -> PAWN_TABLE
            PieceType.KNIGHT -> KNIGHT_TABLE
            PieceType.BISHOP -> BISHOP_TABLE
            PieceType.ROOK -> ROOK_TABLE
            PieceType.QUEEN -> QUEEN_TABLE
            PieceType.KING -> KING_TABLE
        }
        // Tables are written rank 8 (top) .. rank 1 (bottom). White reads mirrored.
        val rank = if (color == PieceColor.WHITE) 7 - pos.rank else pos.rank
        return table[rank * 8 + pos.file]
    }

    private val PAWN_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val KNIGHT_TABLE = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )
    private val BISHOP_TABLE = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )
    private val ROOK_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0
    )
    private val QUEEN_TABLE = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )
    private val KING_TABLE = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20
    )
}
