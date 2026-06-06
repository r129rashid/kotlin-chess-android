package com.rabi.chess.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.rabi.chess.engine.PieceColor
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full-screen overlay showing an armoured pawn-warrior battle whenever a pawn captures
 * another pawn.  Figures are fully filled (helmet, breastplate, limb ovals, sword, shield)
 * — not stick figures.
 *
 * Timeline (2100 ms):
 *   0.00–0.18  both warriors charge in from the edges (running bob, dust)
 *   0.18–0.30  swords clash in the middle — sparks radiate outward  ("CLASH!")
 *   0.30–0.50  attacker winds up the decisive blow
 *   0.50–0.60  decisive strike!
 *   0.55–0.72  impact flash + multi-spark burst
 *   0.65–0.85  defender stumbles and falls
 *   0.82–0.95  attacker raises sword in victory  ("VICTORY!")
 *   0.95–1.00  overlay fades out → GONE → onDone callback
 */
class PawnBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress = 0f
    private var attackerIsWhite = true
    private var onDoneCallback: (() -> Unit)? = null
    private var anim: ValueAnimator? = null

    // ---- Reusable paints (alpha updated per frame) ------------------------

    // White warrior — ivory armour, gold trim
    private val wArmorFill   = mkFill(Color.rgb(238, 230, 205))
    private val wArmorStroke = mkStroke(Color.rgb(168, 128, 24), 0f, round = true)
    // Black warrior — dark-steel armour, silver trim
    private val bArmorFill   = mkFill(Color.rgb(38, 40, 45))
    private val bArmorStroke = mkStroke(Color.rgb(148, 150, 158), 0f, round = true)
    // Visor (same for both)
    private val visorPaint   = mkFill(Color.rgb(12, 8, 4))
    // Sword blade — polished steel
    private val bladeFill    = mkFill(Color.rgb(195, 202, 215))
    private val bladeStroke  = mkStroke(Color.rgb(100, 108, 120), 0f)
    // Sword handle — dark leather
    private val handlePaint  = mkStroke(Color.rgb(90, 55, 18), 0f, round = true)
    // Guard — slightly darker steel
    private val guardPaint   = mkStroke(Color.rgb(140, 145, 155), 0f, round = true)
    // Plume — scarlet for attacker
    private val plumePaint   = mkStroke(Color.rgb(200, 28, 10), 0f, round = true)
    // Impact / sparks
    private val flashPaint   = mkFill(0)   // color set per frame
    private val sparkPaint   = mkStroke(0, 0f, round = true)
    // Ground
    private val groundPaint  = mkStroke(Color.rgb(60, 50, 35), 0f)
    private val dustPaint    = mkFill(Color.argb(80, 180, 160, 120))
    // Text
    private val textPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign    = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ---- Public API -------------------------------------------------------

    fun show(attackerColor: PieceColor, onDone: () -> Unit) {
        attackerIsWhite = (attackerColor == PieceColor.WHITE)
        onDoneCallback  = onDone
        progress        = 0f
        visibility      = VISIBLE
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2100
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

    fun cancel() { anim?.cancel(); visibility = GONE }

    override fun onTouchEvent(event: MotionEvent) = true   // block board taps

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val s       = minOf(w, h) / 780f   // scale: figure ~260 canonical units tall
        val groundY = h * 0.66f
        val cx      = w / 2f

        // === Background ===
        val bgAlpha = ((1f - phase(0.95f, 1.00f)) * 220).toInt()
        canvas.drawColor(Color.argb(bgAlpha, 8, 6, 4))

        // === Ground line ===
        groundPaint.strokeWidth = 2f * s
        groundPaint.alpha       = bgAlpha
        canvas.drawLine(cx - 260f * s, groundY, cx + 260f * s, groundY, groundPaint)

        // === Figure positions ===
        val atkTarget = cx - 90f * s
        val defTarget = cx + 90f * s
        val walkT     = phase(0f, 0.18f)
        val atkX = if (progress < 0.18f) lerp(-w * 0.08f, atkTarget, easeOut(walkT)) else atkTarget
        val defX = if (progress < 0.18f) lerp( w * 1.08f, defTarget, easeOut(walkT)) else defTarget

        // Running bob (vertical bounce during charge-in)
        val runFreq = 6.0   // oscillations during walk-in
        val bob     = if (progress < 0.18f) sin(walkT * PI * runFreq).toFloat() * s * 5f else 0f

        // Dust puffs under feet during charge
        if (progress < 0.22f) {
            val dustAlpha = ((1f - phase(0.15f, 0.22f)) * 120).toInt()
            dustPaint.alpha = dustAlpha
            val puffR = s * 18f * (1f - walkT * 0.3f)
            canvas.drawOval(RectF(atkX - puffR, groundY - puffR * 0.5f, atkX + puffR, groundY + puffR * 0.3f), dustPaint)
            canvas.drawOval(RectF(defX - puffR, groundY - puffR * 0.5f, defX + puffR, groundY + puffR * 0.3f), dustPaint)
        }

        // === Sword angles ===
        // Attacker's weapon arm
        val atkSwordAngle: Float = when {
            progress < 0.18f -> 20f                                                          // neutral run
            progress < 0.28f -> lerp(20f,   0f, phase(0.18f, 0.28f))                       // extend for clash
            progress < 0.30f -> 0f                                                           // clash pose
            progress < 0.50f -> lerp( 0f, -148f, easeIn(phase(0.30f, 0.50f)))              // wind up
            progress < 0.60f -> lerp(-148f,  85f, easeOut(phase(0.50f, 0.60f)))             // STRIKE
            progress < 0.82f -> 85f
            else             -> lerp(85f,  -55f, phase(0.82f, 0.94f))                       // raise: victory
        }
        // Defender's weapon arm  (stays extended for the clash, then gets knocked back)
        val defSwordAngle: Float = when {
            progress < 0.18f -> 20f
            progress < 0.30f -> lerp(20f, 0f, phase(0.18f, 0.30f))   // extend for clash
            progress < 0.65f -> 0f
            else             -> lerp(0f, 40f, phase(0.65f, 0.80f))    // arm pushed back by strike
        }

        // === Defender fall ===
        val defFall  = lerp(0f, 105f, easeOut(phase(0.65f, 0.85f)))
        val defAlpha = lerp(1f,   0f,            phase(0.82f, 0.95f))

        // === Victory ===
        val victoryT = phase(0.82f, 0.95f)

        // === Global fade at the very end ===
        val figAlpha = 1f - phase(0.95f, 1.00f)

        // === Choose paint sets by color ===
        val (atkFill, atkStroke) = if (attackerIsWhite) wArmorFill to wArmorStroke else bArmorFill to bArmorStroke
        val (defFill, defStroke) = if (attackerIsWhite) bArmorFill to bArmorStroke else wArmorFill to wArmorStroke
        val atkA = (255 * figAlpha).toInt()
        val defA = (255 * defAlpha * figAlpha).toInt()

        // === Defender (drawn first so attacker overlaps at the clash) ===
        canvas.save()
        canvas.translate(defX, groundY + bob)
        canvas.rotate(defFall)   // falls backward (clockwise from feet pivot)
        drawPawnWarrior(canvas, s, facingRight = false,
            swordAngle = defSwordAngle, victoryT = 0f,
            fill = defFill, stroke = defStroke,
            isAttacker = false, alpha = defA)
        canvas.restore()

        // === Attacker ===
        canvas.save()
        canvas.translate(atkX, groundY + bob)
        drawPawnWarrior(canvas, s, facingRight = true,
            swordAngle = atkSwordAngle, victoryT = victoryT,
            fill = atkFill, stroke = atkStroke,
            isAttacker = true, alpha = atkA)
        canvas.restore()

        // === Clash sparks (between swords, 0.18–0.38) ===
        val clashT: Float = when {
            progress < 0.18f -> 0f
            progress < 0.26f -> phase(0.18f, 0.26f)
            progress < 0.32f -> 1f
            progress < 0.40f -> 1f - phase(0.32f, 0.40f)
            else             -> 0f
        }
        if (clashT > 0.01f) {
            val clashCX = (atkX + defX) / 2f
            val clashCY = groundY - 155f * s
            drawSparks(canvas, clashCX, clashCY, clashT * figAlpha, s, angleOffset = 0f, count = 10)
        }

        // === Impact flash + burst sparks (0.52–0.72) ===
        val flashT: Float = when {
            progress < 0.52f -> 0f
            progress < 0.59f -> phase(0.52f, 0.59f)
            progress < 0.72f -> 1f - phase(0.59f, 0.72f)
            else             -> 0f
        }
        if (flashT > 0.01f) {
            val fa = figAlpha * flashT
            flashPaint.color = Color.argb((210 * fa).toInt(), 255, 230, 50)
            canvas.drawCircle(cx, groundY - 130f * s, 88f * s * flashT, flashPaint)
            flashPaint.color = Color.argb((160 * fa).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, groundY - 130f * s, 35f * s * flashT, flashPaint)
            drawSparks(canvas, cx, groundY - 130f * s, fa, s, angleOffset = 18f, count = 12)
        }

        // === "CLASH!" label ===
        val clashLabelA: Float = when {
            progress < 0.18f -> 0f
            progress < 0.24f -> phase(0.18f, 0.24f)
            progress < 0.30f -> 1f
            progress < 0.36f -> 1f - phase(0.30f, 0.36f)
            else             -> 0f
        }
        if (clashLabelA > 0.01f) {
            textPaint.color    = Color.argb((220 * clashLabelA * figAlpha).toInt(), 255, 80, 30)
            textPaint.textSize = s * 42f
            canvas.drawText("CLASH!", cx, groundY - 305f * s, textPaint)
        }

        // === "VICTORY!" label ===
        val victoryLabelA: Float = when {
            progress < 0.82f -> 0f
            progress < 0.89f -> phase(0.82f, 0.89f)
            progress < 0.95f -> 1f
            else             -> 1f - phase(0.95f, 1.00f)
        }
        if (victoryLabelA > 0.01f) {
            textPaint.color    = Color.argb((235 * victoryLabelA).toInt(), 255, 215, 0)
            textPaint.textSize = s * 34f
            canvas.drawText("VICTORY!", atkX, groundY - 308f * s, textPaint)
        }
    }

    // ---- Armoured pawn warrior --------------------------------------------

    /**
     * Draws a fully armoured pawn warrior in local coordinates:
     *   (0, 0) = feet centre; figure extends upward (negative y in Canvas space).
     *
     * @param facingRight  true → facing right; sword arm is on the right side.
     * @param swordAngle   degrees relative to horizontal: 0 = arm extended right,
     *                     positive = clockwise (arm swings down), negative = arm raised.
     * @param victoryT     0..1 — free arm raised for celebration.
     * @param isAttacker   true → draw feather plume + sword; false → crest + shield.
     * @param alpha        overall opacity 0..255.
     */
    private fun drawPawnWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, swordAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint,
        isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f

        // Key y-positions (from feet origin, upward = negative)
        val headR     = 32f * s
        val headCY    = -218f * s
        val torsoTop  = headCY + headR + 5f * s
        val torsoBot  = torsoTop + 74f * s
        val torsoHW   = 28f * s          // half-width of breastplate
        val shoulderY = torsoTop + 10f * s
        val hipY      = torsoBot
        val kneeY     = hipY + 54f * s
        val footY     = 0f

        fill.alpha = alpha; stroke.alpha = alpha
        stroke.strokeWidth = 2.5f * s    // outlines on filled shapes

        // ---- Back leg (drawn before body so body overlaps it) ----
        val bkX0 = -d * 6f * s; val bkX1 = -d * 10f * s; val bkX2 = -d * 8f * s
        drawLimbSeg(canvas, bkX0, hipY,  bkX1, kneeY, 13f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, bkX1, kneeY, bkX2, footY,  11f * s, fill, stroke, alpha)

        // ---- Breastplate ----
        val bodyRect = RectF(-torsoHW, torsoTop, torsoHW, torsoBot)
        canvas.drawRoundRect(bodyRect, 9f * s, 9f * s, fill)
        canvas.drawRoundRect(bodyRect, 9f * s, 9f * s, stroke)
        // Chest-plate centre ridge
        stroke.alpha = (alpha * 0.45f).toInt()
        canvas.drawLine(0f, torsoTop + 8f * s, 0f, torsoBot - 8f * s, stroke)
        // Belt line
        canvas.drawLine(-torsoHW + 4f * s, torsoTop + 48f * s,
                         torsoHW - 4f * s, torsoTop + 48f * s, stroke)
        stroke.alpha = alpha

        // ---- Front leg ----
        val frX0 = d * 10f * s; val frX1 = d * 16f * s; val frX2 = d * 14f * s
        drawLimbSeg(canvas, frX0, hipY,  frX1, kneeY, 14f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, frX1, kneeY, frX2, footY,  12f * s, fill, stroke, alpha)
        // Sabaton (foot plate)
        canvas.drawOval(RectF(frX2 - 2f * s, -9f * s, frX2 + d * 22f * s, 0f), fill)
        canvas.drawOval(RectF(frX2 - 2f * s, -9f * s, frX2 + d * 22f * s, 0f), stroke)

        // ---- Shield / off arm ----
        val shldShoulderX = -d * torsoHW * 0.75f
        val shldAngle     = if (isAttacker) lerp(28f, -108f, victoryT.coerceIn(0f, 1f)) else 20f
        val shAR = Math.toRadians(shldAngle.toDouble())
        val shEX = shldShoulderX - d * 32f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 32f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 26f * s * cos(shAR + 0.3).toFloat()
        val shHY = shEY + 26f * s * sin(shAR + 0.3).toFloat()
        drawLimbSeg(canvas, shldShoulderX, shoulderY, shEX, shEY, 13f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 11f * s, fill, stroke, alpha)
        if (!isAttacker) {
            // Defender holds a round shield forward
            drawShield(canvas, shHX - d * 18f * s, shHY - 12f * s, s, fill, stroke, alpha)
        }

        // ---- Weapon arm ----
        val wpShoulderX = d * torsoHW * 0.75f
        val sar = Math.toRadians(swordAngle.toDouble())
        val wpEX = wpShoulderX + d * 35f * s * cos(sar).toFloat()
        val wpEY = shoulderY   +    35f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 27f * s * cos(sar + 0.3).toFloat()
        val wpHY = wpEY +    27f * s * sin(sar + 0.3).toFloat()
        drawLimbSeg(canvas, wpShoulderX, shoulderY, wpEX, wpEY, 14f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 11f * s, fill, stroke, alpha)
        drawSword(canvas, wpHX, wpHY, swordAngle, d, s, alpha)

        // ---- Pauldrons (shoulder caps) — drawn after arms so they sit on top ----
        val wpShPaul = RectF(wpShoulderX - 12f*s, shoulderY - 10f*s, wpShoulderX + d*20f*s, shoulderY + 14f*s)
        canvas.drawOval(wpShPaul, fill); canvas.drawOval(wpShPaul, stroke)
        val shPaul = RectF(shldShoulderX - d*20f*s, shoulderY - 10f*s, shldShoulderX + 12f*s, shoulderY + 14f*s)
        canvas.drawOval(shPaul, fill); canvas.drawOval(shPaul, stroke)

        // ---- Helmet (pawn-ball shape) ----
        canvas.drawCircle(0f, headCY, headR, fill)
        canvas.drawCircle(0f, headCY, headR, stroke)
        // Visor slit
        visorPaint.alpha = alpha
        canvas.drawRoundRect(RectF(-headR * 0.44f, headCY - 7f * s,
                                    headR * 0.44f, headCY + 3f * s),
            3f * s, 3f * s, visorPaint)
        // Chin-guard nub
        canvas.drawOval(RectF(-headR * 0.25f, headCY + headR - 8f * s,
                                headR * 0.25f, headCY + headR + 4f * s), fill)
        canvas.drawOval(RectF(-headR * 0.25f, headCY + headR - 8f * s,
                                headR * 0.25f, headCY + headR + 4f * s), stroke)

        // Plume or crest
        if (isAttacker) drawPlume(canvas, headR, headCY, d, s, alpha)
        else            drawCrest(canvas, headR, headCY, s, alpha)
    }

    // ---- Component drawing helpers ----------------------------------------

    /**
     * Draws one armoured limb segment as a rotated oval between two points.
     * The oval is slightly longer than the point-to-point distance to look rounded at the ends.
     */
    private fun drawLimbSeg(
        canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        thick: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        val dx  = x2 - x1; val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.5f) return
        val ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        canvas.save()
        canvas.translate((x1 + x2) / 2f, (y1 + y2) / 2f)
        canvas.rotate(ang)
        val hw = len / 2f + thick * 0.28f   // slight end-cap extension
        fill.alpha = alpha; stroke.alpha = alpha
        canvas.drawOval(RectF(-hw, -thick / 2f, hw, thick / 2f), fill)
        canvas.drawOval(RectF(-hw, -thick / 2f, hw, thick / 2f), stroke)
        canvas.restore()
    }

    /** Sword: tapered blade path, cross-guard, leather handle. */
    private fun drawSword(canvas: Canvas, hx: Float, hy: Float,
                           angle: Float, d: Float, s: Float, alpha: Int) {
        val a    = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;      val perpY =  cosA
        val bw   = 5f * s   // blade half-width at hilt

        // Tapered blade
        val bladeLen = 68f * s
        val tipX = hx + d * bladeLen * cosA
        val tipY = hy + bladeLen * sinA
        val bladePath = Path().apply {
            moveTo(hx + perpX * bw, hy + perpY * bw)
            lineTo(hx - perpX * bw, hy - perpY * bw)
            lineTo(tipX, tipY)
            close()
        }
        bladeFill.alpha  = alpha; bladeStroke.alpha = alpha
        bladeStroke.strokeWidth = 1.5f * s
        canvas.drawPath(bladePath, bladeFill)
        canvas.drawPath(bladePath, bladeStroke)

        // Cross-guard
        val guardLen = 15f * s
        guardPaint.strokeWidth = 4f * s; guardPaint.alpha = alpha
        canvas.drawLine(hx + perpX * guardLen, hy + perpY * guardLen,
                        hx - perpX * guardLen, hy - perpY * guardLen, guardPaint)

        // Leather handle (toward shoulder from hand)
        val handleLen = 22f * s
        val hEndX = hx - d * handleLen * cosA
        val hEndY = hy - handleLen * sinA
        handlePaint.strokeWidth = 7f * s; handlePaint.alpha = alpha
        canvas.drawLine(hx, hy, hEndX, hEndY, handlePaint)
        // Pommel knob
        bladeFill.alpha = alpha
        canvas.drawCircle(hEndX, hEndY, 5f * s, bladeFill)
        canvas.drawCircle(hEndX, hEndY, 5f * s, guardPaint)
    }

    /** Round shield with inner ring and centre boss. */
    private fun drawShield(canvas: Canvas, cx: Float, cy: Float, s: Float,
                            fill: Paint, stroke: Paint, alpha: Int) {
        val sw = 22f * s; val sh = 28f * s
        fill.alpha = alpha; stroke.alpha = alpha
        val r = RectF(cx - sw, cy - sh, cx + sw, cy + sh)
        canvas.drawOval(r, fill)
        canvas.drawOval(r, stroke)
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawOval(RectF(cx - sw * 0.72f, cy - sh * 0.72f,
                               cx + sw * 0.72f, cy + sh * 0.72f), stroke)
        stroke.alpha = alpha
        canvas.drawCircle(cx, cy, 5.5f * s, stroke)  // boss
    }

    /** Curved feather plume for the attacker (red, flowing curves via cubicTo). */
    private fun drawPlume(canvas: Canvas, headR: Float, headCY: Float,
                           d: Float, s: Float, alpha: Int) {
        plumePaint.alpha = alpha
        // Main plume
        plumePaint.strokeWidth = 7f * s
        val p1 = Path().apply {
            moveTo(d * 8f * s, headCY - headR + 4f * s)
            cubicTo(d * 14f * s, headCY - headR - 28f * s,
                    d * 32f * s, headCY - headR - 50f * s,
                    d * 28f * s, headCY - headR - 78f * s)
        }
        canvas.drawPath(p1, plumePaint)
        // Secondary strand (slightly lighter / thinner)
        plumePaint.strokeWidth = 4.5f * s
        plumePaint.alpha = (alpha * 0.7f).toInt()
        val p2 = Path().apply {
            moveTo(d * 6f * s, headCY - headR + 4f * s)
            cubicTo(d * 22f * s, headCY - headR - 18f * s,
                    d * 40f * s, headCY - headR - 32f * s,
                    d * 38f * s, headCY - headR - 58f * s)
        }
        canvas.drawPath(p2, plumePaint)
        plumePaint.alpha = alpha
    }

    /** Short straight crest for the defender. */
    private fun drawCrest(canvas: Canvas, headR: Float, headCY: Float, s: Float, alpha: Int) {
        guardPaint.strokeWidth = 5f * s; guardPaint.alpha = (alpha * 0.8f).toInt()
        canvas.drawLine(-8f * s, headCY - headR + 2f * s,
                         8f * s, headCY - headR - 14f * s, guardPaint)
        canvas.drawLine( 8f * s, headCY - headR - 14f * s,
                         2f * s, headCY - headR - 26f * s, guardPaint)
    }

    /** Radiating spark lines around a centre point. */
    private fun drawSparks(canvas: Canvas, cx: Float, cy: Float,
                            intensity: Float, s: Float,
                            angleOffset: Float, count: Int) {
        if (intensity < 0.01f) return
        sparkPaint.strokeWidth = 2.5f * s
        val step  = 360f / count
        val inner = 10f * s
        repeat(count) { i ->
            val a     = Math.toRadians(((i * step) + angleOffset).toDouble())
            val outer = (if (i % 2 == 0) 42f else 28f) * s * intensity
            val a2    = (255 * intensity).toInt()
            sparkPaint.color = if (i % 3 == 0) Color.argb(a2, 255, 225, 50)
                               else if (i % 3 == 1) Color.argb(a2, 255, 140, 30)
                               else Color.argb(a2, 255, 255, 200)
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(),
                sparkPaint
            )
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private fun phase(from: Float, to: Float) =
        ((progress - from) / (to - from)).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun easeOut(t: Float): Float { val c = t.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) }
    private fun easeIn(t: Float): Float  { val c = t.coerceIn(0f, 1f); return c * c }

    private fun mkFill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; this.color = color
    }
    private fun mkStroke(color: Int, width: Float, round: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            this.color  = color
            strokeWidth = width
            if (round) { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        }
}
