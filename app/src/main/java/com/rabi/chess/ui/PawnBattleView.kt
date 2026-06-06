package com.rabi.chess.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.rabi.chess.engine.PieceColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen overlay that plays a brief stick-figure battle animation whenever a pawn
 * captures another pawn. Starts GONE; call [show] to animate; returns to GONE when done.
 *
 * Animation timeline (1900 ms):
 *   0.00–0.18  both warriors stride in from opposite edges
 *   0.18–0.28  face-off pause — "BATTLE" label
 *   0.28–0.50  attacker winds sword arm back
 *   0.50–0.60  strike!
 *   0.52–0.68  impact flash
 *   0.60–0.82  defender falls backward
 *   0.80–0.93  defender fades, victor raises both arms — "VICTORY!" label
 *   0.93–1.00  overlay fades out → GONE → onDone callback fires
 */
class PawnBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress = 0f
    private var attackerIsWhite = true
    private var onDoneCallback: (() -> Unit)? = null
    private var anim: ValueAnimator? = null

    // Pre-allocated paints — alpha is updated each frame
    private val whiteFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(249, 249, 247) }
    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val blackFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(17, 17, 17) }
    private val blackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(210, 210, 210); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val swordPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(175, 182, 200); strokeCap = Paint.Cap.ROUND }
    private val flashPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    // ---- Public API -------------------------------------------------------

    fun show(attackerColor: PieceColor, onDone: () -> Unit) {
        attackerIsWhite = (attackerColor == PieceColor.WHITE)
        onDoneCallback = onDone
        progress = 0f
        visibility = VISIBLE
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1900
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    visibility = GONE
                    onDoneCallback?.invoke()
                }
            })
            start()
        }
    }

    /** Cancel mid-animation (e.g. new game). Does NOT fire onDone. */
    fun cancel() {
        anim?.cancel()
        visibility = GONE
    }

    // Touch consumed while visible — blocks interaction with the board behind
    override fun onTouchEvent(event: MotionEvent) = true

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        // Scale: figure canonical height ~245 units; fit ~3 figures into the shorter dimension
        val s       = minOf(w, h) / 740f
        val groundY = h * 0.64f   // y at which feet rest
        val cx      = w / 2f

        // Background — fades out during final 7 %
        val bgAlpha = ((1f - phase(0.93f, 1.00f)) * 215).toInt()
        canvas.drawColor(Color.argb(bgAlpha, 8, 6, 4))

        // ---- Horizontal positions ----
        val atkTarget = cx - 85f * s
        val defTarget = cx + 85f * s
        val walkT     = phase(0f, 0.18f)
        val atkX      = if (progress < 0.18f) lerp(-w * 0.05f, atkTarget, easeOut(walkT)) else atkTarget
        val defX      = if (progress < 0.18f) lerp( w * 1.05f, defTarget, easeOut(walkT)) else defTarget
        val bob       = if (progress < 0.18f) sin(walkT * PI * 5).toFloat() * s * 4f else 0f

        // ---- Sword arm angle (attacker only) ----
        val swordAngle: Float = when {
            progress < 0.28f -> 25f
            progress < 0.50f -> lerp( 25f, -145f, easeIn( phase(0.28f, 0.50f)))  // wind up
            progress < 0.60f -> lerp(-145f,  80f, easeOut(phase(0.50f, 0.60f)))  // strike
            progress < 0.82f -> 80f
            else             -> lerp( 80f, -50f,         phase(0.82f, 0.94f))    // raise: victory
        }

        // ---- Defender fall & fade ----
        val defFall  = lerp(0f, 100f, easeOut(phase(0.60f, 0.82f)))
        val defAlpha = lerp(1f,   0f,           phase(0.80f, 0.93f))

        // ---- Victory arm raise ----
        val victoryT = phase(0.82f, 0.94f)

        // ---- Global figure alpha (end fade) ----
        val figAlpha = 1f - phase(0.93f, 1.00f)

        // ---- Paint setup ----
        val (atkFill, atkStroke) = if (attackerIsWhite) whiteFill to whiteStroke else blackFill to blackStroke
        val (defFill, defStroke) = if (attackerIsWhite) blackFill to blackStroke else whiteFill to whiteStroke
        atkFill.alpha    = (255 * figAlpha).toInt()
        atkStroke.alpha  = (255 * figAlpha).toInt()
        atkStroke.strokeWidth = s * 9f
        defFill.alpha    = (255 * defAlpha * figAlpha).toInt()
        defStroke.alpha  = (255 * defAlpha * figAlpha).toInt()
        defStroke.strokeWidth = s * 9f
        swordPaint.strokeWidth = s * 5f
        swordPaint.alpha = (255 * figAlpha).toInt()

        // ---- Attacker (faces right) ----
        canvas.save()
        canvas.translate(atkX, groundY + bob)
        drawWarrior(canvas, s, facingRight = true,  swordAngle = swordAngle,
            victoryT = victoryT, fill = atkFill, stroke = atkStroke, hasSword = true)
        canvas.restore()

        // ---- Defender (faces left, falls clockwise around feet) ----
        canvas.save()
        canvas.translate(defX, groundY + bob)
        canvas.rotate(defFall)  // clockwise = fall backward (away from attacker)
        drawWarrior(canvas, s, facingRight = false, swordAngle = 20f,
            victoryT = 0f, fill = defFill, stroke = defStroke, hasSword = false)
        canvas.restore()

        // ---- Impact flash ----
        val flashT: Float = when {
            progress < 0.52f -> 0f
            progress < 0.58f -> phase(0.52f, 0.58f)
            progress < 0.68f -> 1f - phase(0.58f, 0.68f)
            else             -> 0f
        }
        if (flashT > 0.01f) {
            val fa = (figAlpha * flashT)
            flashPaint.color = Color.argb((200 * fa).toInt(), 255, 235, 60)
            canvas.drawCircle(cx, groundY - 115f * s, 72f * s * flashT, flashPaint)
            flashPaint.color = Color.argb((140 * fa).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, groundY - 115f * s, 28f * s * flashT, flashPaint)
        }

        // ---- "BATTLE" label (during approach) ----
        val battleAlpha: Float = when {
            progress < 0.05f -> 0f
            progress < 0.14f ->           phase(0.05f, 0.14f)
            progress < 0.26f -> 1f
            progress < 0.32f -> 1f - phase(0.26f, 0.32f)
            else             -> 0f
        }
        if (battleAlpha > 0.01f) {
            textPaint.color     = Color.argb((200 * battleAlpha * figAlpha).toInt(), 255, 200, 50)
            textPaint.textSize  = s * 34f
            canvas.drawText("⚔  BATTLE  ⚔", cx, groundY - 295f * s, textPaint)
        }

        // ---- "VICTORY!" label ----
        val victoryAlpha: Float = when {
            progress < 0.82f -> 0f
            progress < 0.88f ->           phase(0.82f, 0.88f)
            progress < 0.93f -> 1f
            else             -> 1f - phase(0.93f, 1.00f)
        }
        if (victoryAlpha > 0.01f) {
            textPaint.color    = Color.argb((230 * victoryAlpha).toInt(), 255, 215, 0)
            textPaint.textSize = s * 28f
            canvas.drawText("VICTORY!", atkX, groundY - 298f * s, textPaint)
        }
    }

    /**
     * Draws one stick-figure warrior in local coordinates where (0, 0) is the feet centre
     * and the figure extends upward (negative y in Canvas space).
     *
     * @param facingRight  true → figure faces right and sword arm is on the right.
     * @param swordAngle   degrees: 0 = horizontal, positive = clockwise (arm goes down-right).
     * @param victoryT     0..1 how much the free arm has been raised in celebration.
     * @param hasSword     whether to draw the sword extending from the weapon hand.
     */
    private fun drawWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, swordAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, hasSword: Boolean
    ) {
        val d = if (facingRight) 1f else -1f

        // Canonical y positions relative to feet (0)
        val hipY      = -100f * s
        val shoulderY = -175f * s
        val neckY     = -193f * s
        val headCY    = -223f * s
        val headR     =  26f  * s
        val kneeY     = -55f  * s

        // Head
        canvas.drawCircle(0f, headCY, headR, fill)
        canvas.drawCircle(0f, headCY, headR, stroke)

        // Torso
        canvas.drawLine(0f, neckY, 0f, hipY, stroke)

        // Legs — slight forward/back spread
        canvas.drawLine(0f, hipY,  d * 14f * s, kneeY, stroke)
        canvas.drawLine(d * 14f * s,   kneeY,  d * 16f * s, 0f, stroke)
        canvas.drawLine(0f, hipY, -d *  9f * s, kneeY, stroke)
        canvas.drawLine(-d * 9f * s,   kneeY, -d *  6f * s, 0f, stroke)

        // ---- Sword / weapon arm ----
        val sar = Math.toRadians(swordAngle.toDouble())
        val eX  = d  * 38f * s * cos(sar).toFloat()
        val eY  = shoulderY + 38f * s * sin(sar).toFloat()
        val hX  = eX + d * 30f * s * cos(sar + 0.35).toFloat()
        val hY  = eY + 30f * s * sin(sar + 0.35).toFloat()
        canvas.drawLine(0f, shoulderY, eX, eY, stroke)
        canvas.drawLine(eX, eY, hX, hY, stroke)

        if (hasSword) {
            val sar2 = sar - 0.15
            val sX   = hX + d * 55f * s * cos(sar2).toFloat()
            val sY   = hY + 55f * s * sin(sar2).toFloat()
            canvas.drawLine(hX, hY, sX, sY, swordPaint)
            // Cross-guard
            val px = 10f * s * sin(sar2).toFloat()
            val py = -10f * s * cos(sar2).toFloat() * d
            canvas.drawLine(hX - px, hY - py, hX + px, hY + py, swordPaint)
        }

        // ---- Free arm (rises for victory) ----
        val fAngle = lerp(30f, -110f, victoryT.coerceIn(0f, 1f))
        val far    = Math.toRadians(fAngle.toDouble())
        val feX    = -d  * 34f * s * cos(far).toFloat()
        val feY    = shoulderY + 34f * s * sin(far).toFloat()
        val fhX    = feX - d * 28f * s * cos(far + 0.3).toFloat()
        val fhY    = feY + 28f * s * sin(far + 0.3).toFloat()
        canvas.drawLine(0f, shoulderY, feX, feY, stroke)
        canvas.drawLine(feX, feY, fhX, fhY, stroke)
    }

    // ---- Helpers ----------------------------------------------------------

    /** Normalised progress through the sub-phase [from..to], clamped to [0, 1]. */
    private fun phase(from: Float, to: Float) =
        ((progress - from) / (to - from)).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun easeOut(t: Float): Float { val c = t.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) }
    private fun easeIn(t: Float): Float  { val c = t.coerceIn(0f, 1f); return c * c }
}
