package com.rabi.chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveGeneratorTest {

    private fun Board.play(vararg ucis: String) {
        for (uci in ucis) {
            val move = legalMoveFor(this, uci) ?: error("Illegal move in test: $uci")
            makeMove(move)
        }
    }

    /** Find the legal move matching a UCI string (e.g. "e2e4", "e7e8q"). */
    private fun legalMoveFor(board: Board, uci: String): Move? {
        val from = Position.fromAlgebraic(uci.substring(0, 2))
        val to = Position.fromAlgebraic(uci.substring(2, 4))
        val promo = if (uci.length == 5) when (uci[4]) {
            'q' -> PieceType.QUEEN; 'r' -> PieceType.ROOK
            'b' -> PieceType.BISHOP; 'n' -> PieceType.KNIGHT
            else -> null
        } else null
        return MoveGenerator.legalMoves(board)
            .firstOrNull { it.from == from && it.to == to && it.promotion == promo }
    }

    @Test
    fun startingPositionHasTwentyMoves() {
        val board = Board.initial()
        assertEquals(20, MoveGenerator.legalMoves(board).size)
    }

    @Test
    fun foolsMateIsCheckmate() {
        val board = Board.initial()
        // 1. f3 e5 2. g4 Qh4#
        board.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(GameResult.CHECKMATE, MoveGenerator.result(board))
        assertTrue(MoveGenerator.isInCheck(board, PieceColor.WHITE))
    }

    @Test
    fun scholarsMateIsCheckmate() {
        val board = Board.initial()
        // 1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6?? 4. Qxf7#
        board.play("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")
        assertEquals(GameResult.CHECKMATE, MoveGenerator.result(board))
    }

    @Test
    fun stalemateDetected() {
        // Black king on a8, White king c6, White queen b6 — black to move, no legal move, not in check.
        val board = Board.empty(PieceColor.BLACK)
        board.place(Position.fromAlgebraic("a8"), Piece(PieceType.KING, PieceColor.BLACK))
        board.place(Position.fromAlgebraic("c6"), Piece(PieceType.KING, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("b6"), Piece(PieceType.QUEEN, PieceColor.WHITE))
        assertFalse(MoveGenerator.isInCheck(board, PieceColor.BLACK))
        assertEquals(GameResult.STALEMATE, MoveGenerator.result(board))
    }

    @Test
    fun kingSideCastlingIsAvailableAndMovesRook() {
        val board = Board.empty(PieceColor.WHITE)
        board.place(Position.fromAlgebraic("e1"), Piece(PieceType.KING, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("h1"), Piece(PieceType.ROOK, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("e8"), Piece(PieceType.KING, PieceColor.BLACK))
        board.setCastling(CastlingRights(whiteKingSide = true, whiteQueenSide = false,
            blackKingSide = false, blackQueenSide = false))

        val castle = MoveGenerator.legalMoves(board)
            .firstOrNull { it.isCastle && it.to == Position.fromAlgebraic("g1") }
        assertNotNull("King-side castling should be legal", castle)

        board.makeMove(castle!!)
        assertEquals(Piece(PieceType.KING, PieceColor.WHITE), board.pieceAt(Position.fromAlgebraic("g1")))
        assertEquals(Piece(PieceType.ROOK, PieceColor.WHITE), board.pieceAt(Position.fromAlgebraic("f1")))
    }

    @Test
    fun cannotCastleThroughCheck() {
        val board = Board.empty(PieceColor.WHITE)
        board.place(Position.fromAlgebraic("e1"), Piece(PieceType.KING, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("h1"), Piece(PieceType.ROOK, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("e8"), Piece(PieceType.KING, PieceColor.BLACK))
        // Black rook on f8 attacks f1 — the king would pass through check.
        board.place(Position.fromAlgebraic("f8"), Piece(PieceType.ROOK, PieceColor.BLACK))
        board.setCastling(CastlingRights(whiteKingSide = true, whiteQueenSide = false,
            blackKingSide = false, blackQueenSide = false))

        val castle = MoveGenerator.legalMoves(board).firstOrNull { it.isCastle }
        assertEquals(null, castle)
    }

    @Test
    fun enPassantCapturesCorrectPawn() {
        val board = Board.initial()
        // 1. e4 a6 2. e5 d5 (now exd6 e.p. is available)
        board.play("e2e4", "a7a6", "e4e5", "d7d5")
        assertEquals(Position.fromAlgebraic("d6"), board.enPassantTarget)

        val ep = legalMoveFor(board, "e5d6")
        assertNotNull("En passant capture should be legal", ep)
        assertTrue(ep!!.isEnPassant)

        board.makeMove(ep)
        // The captured black pawn on d5 is gone; a white pawn sits on d6.
        assertEquals(null, board.pieceAt(Position.fromAlgebraic("d5")))
        assertEquals(Piece(PieceType.PAWN, PieceColor.WHITE), board.pieceAt(Position.fromAlgebraic("d6")))
    }

    @Test
    fun pawnPromotesToQueen() {
        val board = Board.empty(PieceColor.WHITE)
        board.place(Position.fromAlgebraic("a7"), Piece(PieceType.PAWN, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("e1"), Piece(PieceType.KING, PieceColor.WHITE))
        board.place(Position.fromAlgebraic("e8"), Piece(PieceType.KING, PieceColor.BLACK))

        val promo = legalMoveFor(board, "a7a8q")
        assertNotNull("Promotion to queen should be legal", promo)
        board.makeMove(promo!!)
        assertEquals(Piece(PieceType.QUEEN, PieceColor.WHITE), board.pieceAt(Position.fromAlgebraic("a8")))
    }

    @Test
    fun undoRestoresPositionExactly() {
        val board = Board.initial()
        val before = board.pieces().toSet()
        board.play("e2e4", "e7e5", "g1f3")
        board.undoMove(); board.undoMove(); board.undoMove()
        assertEquals(before, board.pieces().toSet())
        assertEquals(PieceColor.WHITE, board.sideToMove)
        assertEquals(20, MoveGenerator.legalMoves(board).size)
    }
}
