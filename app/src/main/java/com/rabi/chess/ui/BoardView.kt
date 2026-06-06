package com.rabi.chess.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.rabi.chess.R
import com.rabi.chess.engine.Board
import com.rabi.chess.engine.Move
import com.rabi.chess.engine.MoveGenerator
import com.rabi.chess.engine.Piece
import com.rabi.chess.engine.PieceColor
import com.rabi.chess.engine.Position

/**
 * Renders the chess board and pieces (Unicode glyphs, no assets), handles tap-to-move
 * with legal-move highlighting, and animates pieces gliding between squares.
 */
class BoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    interface Listener {
        /** A concrete (non-promotion) move was chosen by the player. */
        fun onMoveChosen(move: Move)
        /** A pawn promotion destination was chosen; the host must pick the piece. */
        fun onPromotionChosen(from: Position, to: Position)
    }

    var listener: Listener? = null
    var whiteBottom: Boolean = true
    /** When false, taps are ignored (AI thinking / animating / game over). */
    var inputEnabled: Boolean = true

    private var board: Board? = null
    private var lastMove: Move? = null
    private var checkSquare: Position? = null

    private var selected: Position? = null
    private var legalForSelected: List<Move> = emptyList()

    // Animation state.
    private var animMove: Move? = null
    private var animPiece: Piece? = null
    private var animProgress = 0f
    private var animator: ValueAnimator? = null

    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f

    private val lightPaint = fill(R.color.board_light)
    private val darkPaint = fill(R.color.board_dark)
    private val selectedPaint = fill(R.color.hl_selected)
    private val lastMovePaint = fill(R.color.hl_lastmove)
    private val checkPaint = fill(R.color.hl_check)
    private val legalDotPaint = fill(R.color.hl_legal)
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
    }

    private val whiteFill = fill(R.color.piece_white)
    private val whiteOutline = stroke(R.color.piece_white_outline)
    private val blackFill = fill(R.color.piece_black)
    private val blackOutline = stroke(R.color.piece_black_outline)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun fill(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, colorRes)
    }

    private fun stroke(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, colorRes)
        textAlign = Paint.Align.CENTER
    }

    /** Replace the displayed position and highlights, then redraw. */
    fun render(board: Board, lastMove: Move?) {
        this.board = board
        this.lastMove = lastMove
        this.checkSquare = if (MoveGenerator.isInCheck(board, board.sideToMove)) {
            board.kingPosition(board.sideToMove)
        } else null
        clearSelection()
        invalidate()
    }

    /** Animate [move] (the board should already reflect the move) then call [onEnd]. */
    fun animateMove(move: Move, movingPiece: Piece, onEnd: () -> Unit) {
        animMove = move
        animPiece = movingPiece
        animProgress = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animMove = null; animPiece = null
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    private fun clearSelection() {
        selected = null
        legalForSelected = emptyList()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h).toFloat()
        cell = size / 8f
        boardLeft = (w - size) / 2f
        boardTop = (h - size) / 2f
        glyphPaint.textSize = cell * 0.80f
        whiteOutline.textSize = cell * 0.80f
        blackOutline.textSize = cell * 0.80f
        // Fixed 1.5dp hairline — does not scale with cell size, so the border is
        // always a crisp edge rather than a proportional rim that looks coin-like.
        val dp = resources.displayMetrics.density
        whiteOutline.strokeWidth = 1.5f * dp
        blackOutline.strokeWidth = 1.5f * dp
        coordPaint.textSize = cell * 0.18f
    }

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val board = this.board ?: return

        // Squares + coordinate labels.
        for (rank in 0..7) for (file in 0..7) {
            val (x, y) = topLeft(file, rank)
            val light = (file + rank) % 2 == 1
            canvas.drawRect(x, y, x + cell, y + cell, if (light) lightPaint else darkPaint)
        }

        // Highlights: last move, check, selected.
        lastMove?.let {
            tint(canvas, it.from, lastMovePaint)
            tint(canvas, it.to, lastMovePaint)
        }
        checkSquare?.let { tint(canvas, it, checkPaint) }
        selected?.let { tint(canvas, it, selectedPaint) }

        // Coordinate labels along edges.
        drawCoordinates(canvas)

        // Legal-move dots.
        for (m in legalForSelected) {
            val (x, y) = topLeft(m.to.file, m.to.rank)
            val cx = x + cell / 2f
            val cy = y + cell / 2f
            val capture = board.pieceAt(m.to) != null || m.isEnPassant
            if (capture) {
                legalDotPaint.style = Paint.Style.STROKE
                legalDotPaint.strokeWidth = cell * 0.08f
                canvas.drawCircle(cx, cy, cell * 0.42f, legalDotPaint)
                legalDotPaint.style = Paint.Style.FILL
            } else {
                canvas.drawCircle(cx, cy, cell * 0.16f, legalDotPaint)
            }
        }

        // Pieces (skip the in-flight destination piece during animation).
        val skip = animMove?.to
        for ((pos, piece) in board.pieces()) {
            if (pos == skip) continue
            val (x, y) = topLeft(pos.file, pos.rank)
            drawGlyph(canvas, piece, x + cell / 2f, y + cell / 2f)
        }

        // In-flight piece.
        val am = animMove
        val ap = animPiece
        if (am != null && ap != null) {
            val (fx, fy) = topLeft(am.from.file, am.from.rank)
            val (tx, ty) = topLeft(am.to.file, am.to.rank)
            val cx = (fx + (tx - fx) * animProgress) + cell / 2f
            val cy = (fy + (ty - fy) * animProgress) + cell / 2f
            drawGlyph(canvas, ap, cx, cy)
        }
    }

    private fun drawGlyph(canvas: Canvas, piece: Piece, cx: Float, cy: Float) {
        val baseline = cy - (glyphPaint.fontMetrics.ascent + glyphPaint.fontMetrics.descent) / 2f
        val (fillP, outlineP) = if (piece.color == PieceColor.WHITE) {
            whiteFill to whiteOutline
        } else {
            blackFill to blackOutline
        }
        fillP.textAlign = Paint.Align.CENTER
        fillP.textSize = glyphPaint.textSize
        canvas.drawText(piece.glyph, cx, baseline, fillP)
        canvas.drawText(piece.glyph, cx, baseline, outlineP)
    }

    private fun drawCoordinates(canvas: Canvas) {
        coordPaint.color = ContextCompat.getColor(context, R.color.board_border)
        for (file in 0..7) {
            val (x, y) = topLeft(file, if (whiteBottom) 0 else 7)
            val label = ('a' + file).toString()
            canvas.drawText(label, x + cell * 0.08f, y + cell - cell * 0.08f, coordPaint)
        }
        for (rank in 0..7) {
            val (x, y) = topLeft(if (whiteBottom) 7 else 0, rank)
            val label = (rank + 1).toString()
            canvas.drawText(label, x + cell - cell * 0.20f, y + cell * 0.22f, coordPaint)
        }
    }

    private fun tint(canvas: Canvas, pos: Position, paint: Paint) {
        val (x, y) = topLeft(pos.file, pos.rank)
        canvas.drawRect(RectF(x, y, x + cell, y + cell), paint)
    }

    /** Top-left pixel of the square, honoring board orientation. */
    private fun topLeft(file: Int, rank: Int): Pair<Float, Float> {
        val col = if (whiteBottom) file else 7 - file
        val row = if (whiteBottom) 7 - rank else rank
        return (boardLeft + col * cell) to (boardTop + row * cell)
    }

    private fun squareAt(px: Float, py: Float): Position? {
        if (px < boardLeft || py < boardTop) return null
        val col = ((px - boardLeft) / cell).toInt()
        val row = ((py - boardTop) / cell).toInt()
        if (col !in 0..7 || row !in 0..7) return null
        val file = if (whiteBottom) col else 7 - col
        val rank = if (whiteBottom) 7 - row else row
        return Position(file, rank)
    }

    // ---- Input ------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        if (!inputEnabled || animMove != null) return true
        val board = this.board ?: return true
        val tapped = squareAt(event.x, event.y) ?: return true

        // If a destination among the selected piece's legal moves was tapped, play it.
        val matching = legalForSelected.filter { it.to == tapped }
        if (selected != null && matching.isNotEmpty()) {
            if (matching.any { it.promotion != null }) {
                listener?.onPromotionChosen(matching.first().from, tapped)
            } else {
                listener?.onMoveChosen(matching.first())
            }
            clearSelection()
            invalidate()
            return true
        }

        // Otherwise (re)select a piece of the side to move.
        val piece = board.pieceAt(tapped)
        if (piece != null && piece.color == board.sideToMove) {
            selected = tapped
            legalForSelected = MoveGenerator.legalMoves(board).filter { it.from == tapped }
        } else {
            clearSelection()
        }
        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
