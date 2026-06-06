package com.rabi.chess.engine

/** Castling availability for both sides. */
data class CastlingRights(
    var whiteKingSide: Boolean = true,
    var whiteQueenSide: Boolean = true,
    var blackKingSide: Boolean = true,
    var blackQueenSide: Boolean = true
) {
    fun copy(): CastlingRights =
        CastlingRights(whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide)
}

/** What we stash so a move can be perfectly reversed. */
private data class UndoRecord(
    val move: Move,
    val movedPiece: Piece,
    val capturedPiece: Piece?,
    val capturedSquare: Position?,   // differs from move.to for en passant
    val prevCastling: CastlingRights,
    val prevEnPassant: Position?,
    val prevHalfmoveClock: Int
)

/**
 * Mutable chess position with make/undo. Pure Kotlin — no Android dependencies.
 * Squares are indexed [rank * 8 + file]; null means empty.
 */
class Board {

    private val squares = arrayOfNulls<Piece>(64)
    var sideToMove: PieceColor = PieceColor.WHITE
        private set
    var castling = CastlingRights()
        private set
    var enPassantTarget: Position? = null
        private set
    var halfmoveClock: Int = 0
        private set

    private val history = ArrayDeque<UndoRecord>()

    /** Number of moves made so far (i.e. how many times [undoMove] can be called). */
    val moveCount: Int get() = history.size

    fun pieceAt(pos: Position): Piece? =
        if (pos.isOnBoard) squares[pos.rank * 8 + pos.file] else null

    fun pieceAt(file: Int, rank: Int): Piece? =
        if (file in 0..7 && rank in 0..7) squares[rank * 8 + file] else null

    private fun set(pos: Position, piece: Piece?) {
        squares[pos.rank * 8 + pos.file] = piece
    }

    /** Sequence of (position, piece) for every occupied square. */
    fun pieces(): List<Pair<Position, Piece>> {
        val out = ArrayList<Pair<Position, Piece>>(32)
        for (rank in 0..7) for (file in 0..7) {
            val p = squares[rank * 8 + file] ?: continue
            out.add(Position(file, rank) to p)
        }
        return out
    }

    fun kingPosition(color: PieceColor): Position? {
        for (rank in 0..7) for (file in 0..7) {
            val p = squares[rank * 8 + file]
            if (p != null && p.type == PieceType.KING && p.color == color) {
                return Position(file, rank)
            }
        }
        return null
    }

    // ---- Move application -------------------------------------------------

    fun makeMove(move: Move) {
        val moving = pieceAt(move.from) ?: error("No piece at ${move.from.algebraic()}")
        val prevCastling = castling.copy()
        val prevEnPassant = enPassantTarget
        val prevHalfmove = halfmoveClock

        // Determine the captured piece and its square (en passant captures elsewhere).
        val capturedSquare: Position?
        val captured: Piece?
        if (move.isEnPassant) {
            capturedSquare = Position(move.to.file, move.from.rank)
            captured = pieceAt(capturedSquare)
        } else {
            capturedSquare = move.to
            captured = pieceAt(move.to)
        }

        history.addLast(
            UndoRecord(move, moving, captured, if (captured != null) capturedSquare else null,
                prevCastling, prevEnPassant, prevHalfmove)
        )

        // Halfmove clock: reset on pawn move or capture, otherwise increment.
        halfmoveClock = if (moving.type == PieceType.PAWN || captured != null) 0 else halfmoveClock + 1

        // Clear captured (handles en passant, where it isn't on move.to).
        if (captured != null) set(capturedSquare, null)

        // Move the piece, applying promotion.
        set(move.from, null)
        val placed = if (move.promotion != null) Piece(move.promotion, moving.color) else moving
        set(move.to, placed)

        // Castling: relocate the rook.
        if (move.isCastle) {
            val rank = move.from.rank
            if (move.to.file == 6) { // king side
                val rook = pieceAt(Position(7, rank))
                set(Position(7, rank), null)
                set(Position(5, rank), rook)
            } else if (move.to.file == 2) { // queen side
                val rook = pieceAt(Position(0, rank))
                set(Position(0, rank), null)
                set(Position(3, rank), rook)
            }
        }

        updateCastlingRights(moving, move, captured, capturedSquare)

        // En passant target: only set on a pawn double-step.
        enPassantTarget = if (moving.type == PieceType.PAWN &&
            kotlin.math.abs(move.to.rank - move.from.rank) == 2
        ) {
            Position(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null

        sideToMove = sideToMove.opposite()
    }

    fun undoMove() {
        val rec = history.removeLastOrNull() ?: return
        sideToMove = sideToMove.opposite()
        castling = rec.prevCastling
        enPassantTarget = rec.prevEnPassant
        halfmoveClock = rec.prevHalfmoveClock

        val move = rec.move
        // Restore the moved piece to its origin (un-promote by using the recorded piece).
        set(move.from, rec.movedPiece)
        set(move.to, null)

        // Undo castling rook relocation.
        if (move.isCastle) {
            val rank = move.from.rank
            if (move.to.file == 6) {
                val rook = pieceAt(Position(5, rank))
                set(Position(5, rank), null)
                set(Position(7, rank), rook)
            } else if (move.to.file == 2) {
                val rook = pieceAt(Position(3, rank))
                set(Position(3, rank), null)
                set(Position(0, rank), rook)
            }
        }

        // Restore captured piece on its real square.
        if (rec.capturedPiece != null && rec.capturedSquare != null) {
            set(rec.capturedSquare, rec.capturedPiece)
        }
    }

    private fun updateCastlingRights(
        moving: Piece,
        move: Move,
        captured: Piece?,
        capturedSquare: Position?
    ) {
        val c = castling.copy()
        // King moves forfeit both rights for that color.
        if (moving.type == PieceType.KING) {
            if (moving.color == PieceColor.WHITE) {
                c.whiteKingSide = false; c.whiteQueenSide = false
            } else {
                c.blackKingSide = false; c.blackQueenSide = false
            }
        }
        // Rook leaving its home square forfeits that side.
        if (moving.type == PieceType.ROOK) {
            when {
                move.from == Position(0, 0) -> c.whiteQueenSide = false
                move.from == Position(7, 0) -> c.whiteKingSide = false
                move.from == Position(0, 7) -> c.blackQueenSide = false
                move.from == Position(7, 7) -> c.blackKingSide = false
            }
        }
        // A rook captured on its home square forfeits the opponent's right.
        if (captured?.type == PieceType.ROOK && capturedSquare != null) {
            when (capturedSquare) {
                Position(0, 0) -> c.whiteQueenSide = false
                Position(7, 0) -> c.whiteKingSide = false
                Position(0, 7) -> c.blackQueenSide = false
                Position(7, 7) -> c.blackKingSide = false
            }
        }
        castling = c
    }

    // ---- Setup ------------------------------------------------------------

    companion object {
        /** A board in the standard starting position. */
        fun initial(): Board {
            val b = Board()
            val back = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            for (file in 0..7) {
                b.set(Position(file, 0), Piece(back[file], PieceColor.WHITE))
                b.set(Position(file, 1), Piece(PieceType.PAWN, PieceColor.WHITE))
                b.set(Position(file, 6), Piece(PieceType.PAWN, PieceColor.BLACK))
                b.set(Position(file, 7), Piece(back[file], PieceColor.BLACK))
            }
            return b
        }

        /** An empty board (used by tests to construct specific positions). */
        fun empty(sideToMove: PieceColor = PieceColor.WHITE): Board {
            val b = Board()
            b.sideToMove = sideToMove
            b.castling = CastlingRights(false, false, false, false)
            return b
        }
    }

    /** Test/util helper: place a piece directly (does not affect history). */
    fun place(pos: Position, piece: Piece?) = set(pos, piece)

    /** Test/util helper: force the side to move. */
    fun setSideToMove(color: PieceColor) { sideToMove = color }

    /** Test/util helper: grant castling rights for setups. */
    fun setCastling(rights: CastlingRights) { castling = rights }
}
