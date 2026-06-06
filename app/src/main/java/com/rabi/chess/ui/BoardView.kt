package com.rabi.chess.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.rabi.chess.R
import com.rabi.chess.engine.Board
import com.rabi.chess.engine.Move
import com.rabi.chess.engine.MoveGenerator
import com.rabi.chess.engine.Piece
import com.rabi.chess.engine.PieceColor
import com.rabi.chess.engine.PieceType
import com.rabi.chess.engine.Position
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders the chess board and pieces (Unicode glyphs, no assets), handles tap-to-move
 * with legal-move highlighting, and hosts four board-side animations:
 *
 *   1. Knight arc jump   — knight glides along a parabolic arc instead of a straight line.
 *   2. King heartbeat    — king's check-square pulses while the king is in check.
 *   3. Promotion glow    — gold radial glow + expanding ring on promotion square.
 *   4. Checkmate curtain — checkmated king tips over, then a dark curtain falls.
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

    // ---- Piece glide animation (existing) ---------------------------------
    private var animMove: Move? = null
    private var animPiece: Piece? = null
    private var animProgress = 0f
    private var animator: ValueAnimator? = null
    /** True when the current glide is for a knight (arc jump). */
    private var isKnightMove = false

    // ---- Board geometry ---------------------------------------------------
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f

    // ---- Square paints ----------------------------------------------------
    private val lightPaint      = fill(R.color.board_light)
    private val darkPaint       = fill(R.color.board_dark)
    private val selectedPaint   = fill(R.color.hl_selected)
    private val lastMovePaint   = fill(R.color.hl_lastmove)
    private val checkPaint      = fill(R.color.hl_check)
    private val legalDotPaint   = fill(R.color.hl_legal)
    private val coordPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f }

    // ---- Piece paints -----------------------------------------------------
    private val whiteFill    = fill(R.color.piece_white)
    private val whiteOutline = stroke(R.color.piece_white_outline)
    private val blackFill    = fill(R.color.piece_black)
    private val blackOutline = stroke(R.color.piece_black_outline)
    private val glyphPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // ---- King heartbeat (animation 2) -------------------------------------
    private var heartbeatScale    = 1f
    private var heartbeatAnimator: ValueAnimator? = null

    // ---- Promotion glow (animation 3) -------------------------------------
    private var glowPos      : Position? = null
    private var glowProgress = 0f
    private var glowAnimator : ValueAnimator? = null
    private val glowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        color      = Color.argb(170, 255, 215, 0)
    }

    // ---- Checkmate curtain (animation 4) ----------------------------------
    /** -1 = inactive; 0..1 = curtain falling. */
    private var curtainProgress  = -1f
    private var curtainKingPos   : Position? = null
    private var curtainKingPiece : Piece? = null
    private var curtainAnimator  : ValueAnimator? = null
    private val curtainBgPaint   = Paint()

    // ---- Helpers ----------------------------------------------------------

    private fun fill(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, colorRes)
    }

    private fun stroke(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        color      = ContextCompat.getColor(context, colorRes)
        textAlign  = Paint.Align.CENTER
    }

    // ---- Public API -------------------------------------------------------

    /** Replace the displayed position and highlights, then redraw. */
    fun render(board: Board, lastMove: Move?) {
        this.board    = board
        this.lastMove = lastMove
        val inCheck   = MoveGenerator.isInCheck(board, board.sideToMove)
        this.checkSquare = if (inCheck) board.kingPosition(board.sideToMove) else null
        if (inCheck) startHeartbeat() else stopHeartbeat()
        clearSelection()
        invalidate()
    }

    /** Animate [move] (board already reflects the move) then call [onEnd]. */
    fun animateMove(move: Move, movingPiece: Piece, onEnd: () -> Unit) {
        isKnightMove = (movingPiece.type == PieceType.KNIGHT)
        animMove     = move
        animPiece    = movingPiece
        animProgress = 0f
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = if (isKnightMove) 270 else 220  // knight gets a touch more air time
            interpolator = DecelerateInterpolator()
            addUpdateListener { animProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animMove = null; animPiece = null; isKnightMove = false
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    /**
     * Play a gold radial glow at [pos] to celebrate a pawn promotion.
     * Fires [onEnd] when the 700 ms animation completes.
     * Call AFTER render() so the promoted piece is already on screen.
     */
    fun showPromotionGlow(pos: Position, onEnd: () -> Unit) {
        glowPos      = pos
        glowProgress = 0f
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            addUpdateListener { glowProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    glowPos      = null
                    glowProgress = 0f
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    /**
     * Tip the checkmated king over, then sweep a dark curtain down the board.
     * Fires [onEnd] (≈ 950 ms later) so the caller can show the result card.
     * Must be called AFTER render() so [checkSquare] and [board] are current.
     */
    fun playCheckmateCurtain(onEnd: () -> Unit) {
        stopHeartbeat()
        curtainKingPos   = checkSquare
        curtainKingPiece = checkSquare?.let { board?.pieceAt(it) }
        curtainProgress  = 0f
        curtainAnimator?.cancel()
        curtainAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = 950
            interpolator = DecelerateInterpolator()
            addUpdateListener { curtainProgress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    curtainProgress = -1f
                    invalidate()
                    onEnd()
                }
            })
            start()
        }
    }

    /** Cancel all running animations (call from GameActivity.resetGame). */
    fun cancelAnimations() {
        animator?.cancel()
        heartbeatAnimator?.cancel()
        glowAnimator?.cancel()
        curtainAnimator?.cancel()
        animMove         = null; animPiece        = null; isKnightMove  = false
        heartbeatScale   = 1f
        glowPos          = null; glowProgress     = 0f
        curtainProgress  = -1f; curtainKingPos   = null; curtainKingPiece = null
        invalidate()
    }

    // ---- Sizing -----------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w    = MeasureSpec.getSize(widthMeasureSpec)
        val h    = MeasureSpec.getSize(heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h).toFloat()
        cell      = size / 8f
        boardLeft = (w - size) / 2f
        boardTop  = (h - size) / 2f
        glyphPaint.textSize    = cell * 0.80f
        whiteOutline.textSize  = cell * 0.80f
        blackOutline.textSize  = cell * 0.80f
        // Fixed 1.5 dp hairline — crisp, not coin-like
        val dp = resources.displayMetrics.density
        whiteOutline.strokeWidth = 1.5f * dp
        blackOutline.strokeWidth = 1.5f * dp
        coordPaint.textSize = cell * 0.18f
    }

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val board = this.board ?: return

        // 1. Squares
        for (rank in 0..7) for (file in 0..7) {
            val (x, y) = topLeft(file, rank)
            val light   = (file + rank) % 2 == 1
            canvas.drawRect(x, y, x + cell, y + cell, if (light) lightPaint else darkPaint)
        }

        // 2. Highlights: last move, check (with heartbeat pulse), selection
        lastMove?.let {
            tint(canvas, it.from, lastMovePaint)
            tint(canvas, it.to,   lastMovePaint)
        }
        checkSquare?.let {
            val (x, y)  = topLeft(it.file, it.rank)
            val expand  = (heartbeatScale - 1f) * cell / 2f
            canvas.drawRect(RectF(x - expand, y - expand, x + cell + expand, y + cell + expand), checkPaint)
        }
        selected?.let { tint(canvas, it, selectedPaint) }

        // 3. Rank / file labels
        drawCoordinates(canvas)

        // 4. Legal-move dots
        for (m in legalForSelected) {
            val (x, y)  = topLeft(m.to.file, m.to.rank)
            val mcx     = x + cell / 2f
            val mcy     = y + cell / 2f
            val capture = board.pieceAt(m.to) != null || m.isEnPassant
            if (capture) {
                legalDotPaint.style       = Paint.Style.STROKE
                legalDotPaint.strokeWidth = cell * 0.08f
                canvas.drawCircle(mcx, mcy, cell * 0.42f, legalDotPaint)
                legalDotPaint.style = Paint.Style.FILL
            } else {
                canvas.drawCircle(mcx, mcy, cell * 0.16f, legalDotPaint)
            }
        }

        // 5. Pieces — skip: (a) in-flight destination, (b) king during curtain
        val skipDest   = animMove?.to
        val skipCurtain = if (curtainProgress >= 0f) curtainKingPos else null
        for ((pos, piece) in board.pieces()) {
            if (pos == skipDest)    continue
            if (pos == skipCurtain) continue
            val (x, y) = topLeft(pos.file, pos.rank)
            drawGlyph(canvas, piece, x + cell / 2f, y + cell / 2f)
        }

        // 6. In-flight piece (glide + optional knight arc)
        val am = animMove
        val ap = animPiece
        if (am != null && ap != null) {
            val (fx, fy) = topLeft(am.from.file, am.from.rank)
            val (tx, ty) = topLeft(am.to.file,   am.to.rank)
            val lx       = (fx + (tx - fx) * animProgress) + cell / 2f
            val ly       = (fy + (ty - fy) * animProgress) + cell / 2f
            // Animation 1 — Knight arc: sine-wave lift, peak = 1.5 squares at midpoint
            val arcLift  = if (isKnightMove) -sin(animProgress * PI).toFloat() * cell * 1.5f else 0f
            drawGlyph(canvas, ap, lx, ly + arcLift)
        }

        // 7. Promotion glow (animation 3)
        glowPos?.let { gp ->
            val (x, y)  = topLeft(gp.file, gp.rank)
            val gcx     = x + cell / 2f
            val gcy     = y + cell / 2f
            val alpha   = when {
                glowProgress < 0.35f -> glowProgress / 0.35f
                glowProgress < 0.60f -> 1f
                else                 -> 1f - (glowProgress - 0.60f) / 0.40f
            }.coerceIn(0f, 1f)
            // Radial gradient pulse
            val gradient = RadialGradient(
                gcx, gcy, cell * 0.85f,
                Color.argb((210 * alpha).toInt(), 255, 215, 0),
                Color.argb(0, 255, 180, 0),
                Shader.TileMode.CLAMP
            )
            val glowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            glowFillPaint.shader = gradient
            canvas.drawCircle(gcx, gcy, cell * 0.85f, glowFillPaint)
            // Expanding ring
            glowRingPaint.strokeWidth = cell * 0.04f
            glowRingPaint.alpha       = (170 * alpha).toInt()
            canvas.drawCircle(gcx, gcy, cell * (0.25f + glowProgress * 0.55f), glowRingPaint)
        }

        // 8. Checkmate curtain (animation 4)
        if (curtainProgress >= 0f) {
            val kp     = curtainKingPos
            val kPiece = curtainKingPiece
            if (kp != null && kPiece != null) {
                val (kx, ky) = topLeft(kp.file, kp.rank)
                val kcx = kx + cell / 2f
                val kcy = ky + cell / 2f

                // Phase 1 (0.0 – 0.5): king tips 90° around base
                val fallT  = easeOut((curtainProgress / 0.5f).coerceIn(0f, 1f))
                canvas.save()
                canvas.rotate(90f * fallT, kcx, kcy + cell * 0.28f)
                drawGlyph(canvas, kPiece, kcx, kcy)
                canvas.restore()

                // Phase 2 (0.35 – 1.0): dark curtain sweeps down
                val curtainT = easeIn(((curtainProgress - 0.35f) / 0.65f).coerceIn(0f, 1f))
                if (curtainT > 0f) {
                    val curtainBottom = boardTop + cell * 8f * curtainT
                    curtainBgPaint.color = Color.argb((215 * curtainT).toInt(), 8, 6, 4)
                    canvas.drawRect(boardLeft, boardTop, boardLeft + cell * 8f, curtainBottom, curtainBgPaint)
                }
            }
        }
    }

    // ---- Piece glyph rendering --------------------------------------------

    private fun drawGlyph(canvas: Canvas, piece: Piece, cx: Float, cy: Float) {
        val baseline    = cy - (glyphPaint.fontMetrics.ascent + glyphPaint.fontMetrics.descent) / 2f
        val (fillP, outP) = if (piece.color == PieceColor.WHITE) whiteFill to whiteOutline
                           else blackFill to blackOutline
        fillP.textAlign = Paint.Align.CENTER
        fillP.textSize  = glyphPaint.textSize
        canvas.drawText(piece.glyph, cx, baseline, fillP)
        canvas.drawText(piece.glyph, cx, baseline, outP)
    }

    // ---- Coordinate labels ------------------------------------------------

    private fun drawCoordinates(canvas: Canvas) {
        coordPaint.color = ContextCompat.getColor(context, R.color.board_border)
        for (file in 0..7) {
            val (x, y) = topLeft(file, if (whiteBottom) 0 else 7)
            canvas.drawText(('a' + file).toString(), x + cell * 0.08f, y + cell - cell * 0.08f, coordPaint)
        }
        for (rank in 0..7) {
            val (x, y) = topLeft(if (whiteBottom) 7 else 0, rank)
            canvas.drawText((rank + 1).toString(), x + cell - cell * 0.20f, y + cell * 0.22f, coordPaint)
        }
    }

    // ---- Highlight helpers ------------------------------------------------

    private fun tint(canvas: Canvas, pos: Position, paint: Paint) {
        val (x, y) = topLeft(pos.file, pos.rank)
        canvas.drawRect(RectF(x, y, x + cell, y + cell), paint)
    }

    /** Top-left pixel of the square, honouring board orientation. */
    private fun topLeft(file: Int, rank: Int): Pair<Float, Float> {
        val col = if (whiteBottom) file       else 7 - file
        val row = if (whiteBottom) 7 - rank   else rank
        return (boardLeft + col * cell) to (boardTop + row * cell)
    }

    private fun squareAt(px: Float, py: Float): Position? {
        if (px < boardLeft || py < boardTop) return null
        val col = ((px - boardLeft) / cell).toInt()
        val row = ((py - boardTop)  / cell).toInt()
        if (col !in 0..7 || row !in 0..7) return null
        val file = if (whiteBottom) col       else 7 - col
        val rank = if (whiteBottom) 7 - row   else row
        return Position(file, rank)
    }

    // ---- Input handling ---------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)
        if (!inputEnabled || animMove != null) return true
        val board  = this.board ?: return true
        val tapped = squareAt(event.x, event.y) ?: return true

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

        val piece = board.pieceAt(tapped)
        if (piece != null && piece.color == board.sideToMove) {
            selected           = tapped
            legalForSelected   = MoveGenerator.legalMoves(board).filter { it.from == tapped }
        } else {
            clearSelection()
        }
        invalidate()
        return true
    }

    // ---- Lifecycle --------------------------------------------------------

    override fun onDetachedFromWindow() {
        animator?.cancel()
        heartbeatAnimator?.cancel()
        glowAnimator?.cancel()
        curtainAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    // ---- Private helpers --------------------------------------------------

    private fun clearSelection() {
        selected         = null
        legalForSelected = emptyList()
    }

    /** Start the repeating heartbeat pulse on the king's check square. */
    private fun startHeartbeat() {
        if (heartbeatAnimator?.isRunning == true) return
        heartbeatAnimator = ValueAnimator.ofFloat(1f, 1.12f).apply {
            duration    = 580
            repeatMode  = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { heartbeatScale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Stop the heartbeat and reset the check square to its resting size. */
    private fun stopHeartbeat() {
        heartbeatAnimator?.cancel()
        heartbeatAnimator = null
        heartbeatScale    = 1f
        invalidate()
    }

    private fun easeOut(t: Float): Float { val c = t.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) }
    private fun easeIn(t: Float): Float  { val c = t.coerceIn(0f, 1f); return c * c }
}
