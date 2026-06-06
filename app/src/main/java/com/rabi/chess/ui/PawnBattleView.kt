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
import com.rabi.chess.engine.PieceType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full-screen overlay showing an armoured warrior battle whenever any piece captures another.
 * Each piece type has a distinct warrior design:
 *   Pawn   — ball helm + scarlet plume, sword + round shield
 *   Knight — angular great helm, lance + small buckler, wide pauldrons
 *   Bishop — tall mitre + gold cross, crozier staff, flowing robes (no legs)
 *   Rook   — tower helm with battlements, war axe + tall tower shield, extra-wide
 *   Queen  — open crown + triple plume, rapier + parrying dagger, sleek breastplate
 *
 * Timeline (2100 ms):
 *   0.00–0.18  both warriors charge in from the edges
 *   0.18–0.30  weapons clash — sparks radiate
 *   0.30–0.50  attacker winds up the decisive blow
 *   0.50–0.60  decisive strike!
 *   0.55–0.72  impact flash + burst sparks
 *   0.65–0.85  defender stumbles and falls
 *   0.82–0.95  attacker raises weapon in victory
 *   0.95–1.00  overlay fades → GONE → onDone callback
 *
 * Tap anywhere after 10 % to skip.
 */
class PawnBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var progress       = 0f
    private var attackerIsWhite = true
    private var attackerType   = PieceType.PAWN
    private var defenderType   = PieceType.PAWN
    private var onDoneCallback: (() -> Unit)? = null
    private var anim: ValueAnimator? = null

    // ---- Reusable paints -------------------------------------------------

    // White warrior — ivory armour, gold trim
    private val wArmorFill   = mkFill(Color.rgb(238, 230, 205))
    private val wArmorStroke = mkStroke(Color.rgb(168, 128, 24), 0f, round = true)
    // Black warrior — dark-steel armour, silver trim
    private val bArmorFill   = mkFill(Color.rgb(38, 40, 45))
    private val bArmorStroke = mkStroke(Color.rgb(148, 150, 158), 0f, round = true)
    // Visor / dark accents
    private val visorPaint   = mkFill(Color.rgb(12, 8, 4))
    // Weapon blade — polished steel
    private val bladeFill    = mkFill(Color.rgb(195, 202, 215))
    private val bladeStroke  = mkStroke(Color.rgb(100, 108, 120), 0f)
    // Weapon handle — dark leather
    private val handlePaint  = mkStroke(Color.rgb(90, 55, 18), 0f, round = true)
    // Cross-guard — slightly darker steel
    private val guardPaint   = mkStroke(Color.rgb(140, 145, 155), 0f, round = true)
    // Plume — scarlet (pawn / queen first strand)
    private val plumePaint   = mkStroke(Color.rgb(200, 28, 10), 0f, round = true)
    // Gold — bishop mitre cross, queen crown, crozier
    private val goldFill     = mkFill(Color.rgb(212, 175, 55))
    private val goldStroke   = mkStroke(Color.rgb(212, 175, 55), 0f, round = true)
    // Purple — queen plume second strand
    private val purplePaint  = mkStroke(Color.rgb(148, 0, 211), 0f, round = true)
    // Blue — knight lance grip
    private val bluePaint    = mkStroke(Color.rgb(30, 90, 200), 0f, round = true)
    // Impact / sparks
    private val flashPaint   = mkFill(0)
    private val sparkPaint   = mkStroke(0, 0f, round = true)
    // Ground / dust
    private val groundPaint  = mkStroke(Color.rgb(60, 50, 35), 0f)
    private val dustPaint    = mkFill(Color.argb(80, 180, 160, 120))
    // Text
    private val textPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign      = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ---- Public API -------------------------------------------------------

    fun show(
        attackerType: PieceType,
        attackerColor: PieceColor,
        defenderType: PieceType,
        onDone: () -> Unit
    ) {
        this.attackerType  = attackerType
        this.defenderType  = defenderType
        attackerIsWhite    = (attackerColor == PieceColor.WHITE)
        onDoneCallback     = onDone
        progress           = 0f
        visibility         = VISIBLE
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && progress > 0.10f) anim?.end()
        return true
    }

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val s       = minOf(w, h) / 780f
        val groundY = h * 0.66f
        val cx      = w / 2f

        // Background
        val bgAlpha = ((1f - phase(0.95f, 1.00f)) * 220).toInt()
        canvas.drawColor(Color.argb(bgAlpha, 8, 6, 4))

        // Ground line
        groundPaint.strokeWidth = 2f * s; groundPaint.alpha = bgAlpha
        canvas.drawLine(cx - 260f * s, groundY, cx + 260f * s, groundY, groundPaint)

        // Figure positions — charge in from edges
        val atkTarget = cx - 90f * s
        val defTarget = cx + 90f * s
        val walkT = phase(0f, 0.18f)
        val atkX  = if (progress < 0.18f) lerp(-w * 0.08f, atkTarget, easeOut(walkT)) else atkTarget
        val defX  = if (progress < 0.18f) lerp( w * 1.08f, defTarget, easeOut(walkT)) else defTarget
        val bob   = if (progress < 0.18f) sin(walkT * PI * 6.0).toFloat() * s * 5f else 0f

        // Dust puffs during charge
        if (progress < 0.22f) {
            val dustAlpha = ((1f - phase(0.15f, 0.22f)) * 120).toInt()
            dustPaint.alpha = dustAlpha
            val puffR = s * 18f * (1f - walkT * 0.3f)
            canvas.drawOval(RectF(atkX - puffR, groundY - puffR * 0.5f, atkX + puffR, groundY + puffR * 0.3f), dustPaint)
            canvas.drawOval(RectF(defX - puffR, groundY - puffR * 0.5f, defX + puffR, groundY + puffR * 0.3f), dustPaint)
        }

        // Weapon angles (same timing for all piece types)
        val atkWeaponAngle: Float = when {
            progress < 0.18f -> 20f
            progress < 0.28f -> lerp(20f,    0f, phase(0.18f, 0.28f))
            progress < 0.30f -> 0f
            progress < 0.50f -> lerp( 0f, -148f, easeIn(phase(0.30f, 0.50f)))
            progress < 0.60f -> lerp(-148f,  85f, easeOut(phase(0.50f, 0.60f)))
            progress < 0.82f -> 85f
            else             -> lerp(85f,  -55f, phase(0.82f, 0.94f))
        }
        val defWeaponAngle: Float = when {
            progress < 0.18f -> 20f
            progress < 0.30f -> lerp(20f, 0f, phase(0.18f, 0.30f))
            progress < 0.65f -> 0f
            else             -> lerp(0f, 40f, phase(0.65f, 0.80f))
        }

        val defFall  = lerp(0f, 105f, easeOut(phase(0.65f, 0.85f)))
        val defAlpha = lerp(1f,   0f,           phase(0.82f, 0.95f))
        val victoryT = phase(0.82f, 0.95f)
        val figAlpha = 1f - phase(0.95f, 1.00f)

        val (atkFill, atkStroke) = if (attackerIsWhite) wArmorFill to wArmorStroke else bArmorFill to bArmorStroke
        val (defFill, defStroke) = if (attackerIsWhite) bArmorFill to bArmorStroke else wArmorFill to wArmorStroke
        val atkA = (255 * figAlpha).toInt()
        val defA = (255 * defAlpha * figAlpha).toInt()

        // Defender drawn first so attacker overlaps at the clash
        canvas.save()
        canvas.translate(defX, groundY + bob)
        canvas.rotate(defFall)
        drawWarrior(canvas, defenderType, s, facingRight = false,
            weaponAngle = defWeaponAngle, victoryT = 0f,
            fill = defFill, stroke = defStroke, isAttacker = false, alpha = defA)
        canvas.restore()

        // Attacker
        canvas.save()
        canvas.translate(atkX, groundY + bob)
        drawWarrior(canvas, attackerType, s, facingRight = true,
            weaponAngle = atkWeaponAngle, victoryT = victoryT,
            fill = atkFill, stroke = atkStroke, isAttacker = true, alpha = atkA)
        canvas.restore()

        // Clash sparks
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
            drawSparks(canvas, clashCX, clashCY, clashT * figAlpha, s, 0f, 10, attackerType)
        }

        // Impact flash + burst sparks
        val flashT: Float = when {
            progress < 0.52f -> 0f
            progress < 0.59f -> phase(0.52f, 0.59f)
            progress < 0.72f -> 1f - phase(0.59f, 0.72f)
            else             -> 0f
        }
        if (flashT > 0.01f) {
            val fa = figAlpha * flashT
            val fc = sparkColors(attackerType)
            flashPaint.color = Color.argb((210 * fa).toInt(), Color.red(fc[0]), Color.green(fc[0]), Color.blue(fc[0]))
            canvas.drawCircle(cx, groundY - 130f * s, 88f * s * flashT, flashPaint)
            flashPaint.color = Color.argb((160 * fa).toInt(), 255, 255, 255)
            canvas.drawCircle(cx, groundY - 130f * s, 35f * s * flashT, flashPaint)
            drawSparks(canvas, cx, groundY - 130f * s, fa, s, 18f, 12, attackerType)
        }

        // "CLASH!" label
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

        // "VICTORY!" label
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

    // ---- Warrior dispatcher -----------------------------------------------

    private fun drawWarrior(
        canvas: Canvas, type: PieceType, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) = when (type) {
        PieceType.PAWN   -> drawPawnWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
        PieceType.KNIGHT -> drawKnightWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
        PieceType.BISHOP -> drawBishopWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
        PieceType.ROOK   -> drawRookWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
        PieceType.QUEEN  -> drawQueenWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
        PieceType.KING   -> drawPawnWarrior(canvas, s, facingRight, weaponAngle, victoryT, fill, stroke, isAttacker, alpha)
    }

    // ---- Pawn Warrior (ball helm + scarlet plume, sword + round shield) ----

    private fun drawPawnWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f
        val headR    = 32f * s
        val headCY   = -218f * s
        val torsoTop = headCY + headR + 5f * s
        val torsoBot = torsoTop + 74f * s
        val torsoHW  = 28f * s
        val shoulderY = torsoTop + 10f * s
        val hipY  = torsoBot
        val kneeY = hipY + 54f * s

        fill.alpha = alpha; stroke.alpha = alpha; stroke.strokeWidth = 2.5f * s

        // Back leg
        drawLimbSeg(canvas, -d * 6f * s, hipY, -d * 10f * s, kneeY, 13f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, -d * 10f * s, kneeY, -d * 8f * s, 0f, 11f * s, fill, stroke, alpha)

        // Breastplate
        val bodyRect = RectF(-torsoHW, torsoTop, torsoHW, torsoBot)
        canvas.drawRoundRect(bodyRect, 9f * s, 9f * s, fill)
        canvas.drawRoundRect(bodyRect, 9f * s, 9f * s, stroke)
        stroke.alpha = (alpha * 0.45f).toInt()
        canvas.drawLine(0f, torsoTop + 8f * s, 0f, torsoBot - 8f * s, stroke)
        canvas.drawLine(-torsoHW + 4f * s, torsoTop + 48f * s, torsoHW - 4f * s, torsoTop + 48f * s, stroke)
        stroke.alpha = alpha

        // Front leg + sabaton
        val frX1 = d * 16f * s; val frX2 = d * 14f * s
        drawLimbSeg(canvas, d * 10f * s, hipY, frX1, kneeY, 14f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, frX1, kneeY, frX2, 0f, 12f * s, fill, stroke, alpha)
        canvas.drawOval(RectF(frX2 - 2f * s, -9f * s, frX2 + d * 22f * s, 0f), fill)
        canvas.drawOval(RectF(frX2 - 2f * s, -9f * s, frX2 + d * 22f * s, 0f), stroke)

        // Off arm (shield / raised in victory)
        val shX = -d * torsoHW * 0.75f
        val shA = if (isAttacker) lerp(28f, -108f, victoryT.coerceIn(0f, 1f)) else 20f
        val shAR = Math.toRadians(shA.toDouble())
        val shEX = shX - d * 32f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 32f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 26f * s * cos(shAR + 0.3).toFloat()
        val shHY = shEY + 26f * s * sin(shAR + 0.3).toFloat()
        drawLimbSeg(canvas, shX, shoulderY, shEX, shEY, 13f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 11f * s, fill, stroke, alpha)
        if (!isAttacker) drawRoundShield(canvas, shHX - d * 18f * s, shHY - 12f * s, s, fill, stroke, alpha)

        // Weapon arm
        val wpX = d * torsoHW * 0.75f
        val sar = Math.toRadians(weaponAngle.toDouble())
        val wpEX = wpX + d * 35f * s * cos(sar).toFloat()
        val wpEY = shoulderY + 35f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 27f * s * cos(sar + 0.3).toFloat()
        val wpHY = wpEY + 27f * s * sin(sar + 0.3).toFloat()
        drawLimbSeg(canvas, wpX, shoulderY, wpEX, wpEY, 14f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 11f * s, fill, stroke, alpha)
        drawSword(canvas, wpHX, wpHY, weaponAngle, d, s, alpha)

        // Pauldrons
        canvas.drawOval(RectF(wpX - 12f*s, shoulderY - 10f*s, wpX + d*20f*s, shoulderY + 14f*s), fill)
        canvas.drawOval(RectF(wpX - 12f*s, shoulderY - 10f*s, wpX + d*20f*s, shoulderY + 14f*s), stroke)
        canvas.drawOval(RectF(shX - d*20f*s, shoulderY - 10f*s, shX + 12f*s, shoulderY + 14f*s), fill)
        canvas.drawOval(RectF(shX - d*20f*s, shoulderY - 10f*s, shX + 12f*s, shoulderY + 14f*s), stroke)

        // Ball helm
        canvas.drawCircle(0f, headCY, headR, fill)
        canvas.drawCircle(0f, headCY, headR, stroke)
        visorPaint.alpha = alpha
        canvas.drawRoundRect(RectF(-headR * 0.44f, headCY - 7f * s, headR * 0.44f, headCY + 3f * s), 3f * s, 3f * s, visorPaint)
        canvas.drawOval(RectF(-headR * 0.25f, headCY + headR - 8f * s, headR * 0.25f, headCY + headR + 4f * s), fill)
        canvas.drawOval(RectF(-headR * 0.25f, headCY + headR - 8f * s, headR * 0.25f, headCY + headR + 4f * s), stroke)

        if (isAttacker) drawScarletPlume(canvas, headR, headCY, d, s, alpha)
        else            drawCrest(canvas, headR, headCY, s, alpha)
    }

    // ---- Knight Warrior (angular great helm, lance + small buckler) --------

    private fun drawKnightWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f
        val headR    = 30f * s
        val headCY   = -222f * s
        val torsoTop = headCY + headR + 4f * s
        val torsoBot = torsoTop + 80f * s
        val torsoHW  = 34f * s
        val shoulderY = torsoTop + 10f * s
        val hipY  = torsoBot
        val kneeY = hipY + 52f * s

        fill.alpha = alpha; stroke.alpha = alpha; stroke.strokeWidth = 2.5f * s

        // Wide legs
        drawLimbSeg(canvas, -d * 7f * s, hipY, -d * 12f * s, kneeY, 15f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, -d * 12f * s, kneeY, -d * 10f * s, 0f, 12f * s, fill, stroke, alpha)

        // Wide breastplate
        val bodyRect = RectF(-torsoHW, torsoTop, torsoHW, torsoBot)
        canvas.drawRoundRect(bodyRect, 8f * s, 8f * s, fill)
        canvas.drawRoundRect(bodyRect, 8f * s, 8f * s, stroke)
        stroke.alpha = (alpha * 0.4f).toInt()
        canvas.drawLine(-torsoHW + 4f*s, torsoTop + 40f*s, torsoHW - 4f*s, torsoTop + 40f*s, stroke)
        stroke.alpha = alpha

        // Front leg + sabaton
        val frX1 = d * 20f * s; val frX2 = d * 18f * s
        drawLimbSeg(canvas, d * 13f * s, hipY, frX1, kneeY, 16f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, frX1, kneeY, frX2, 0f, 13f * s, fill, stroke, alpha)
        canvas.drawOval(RectF(frX2 - 2f*s, -9f*s, frX2 + d*24f*s, 0f), fill)
        canvas.drawOval(RectF(frX2 - 2f*s, -9f*s, frX2 + d*24f*s, 0f), stroke)

        // Off arm (small buckler or raised)
        val shX = -d * torsoHW * 0.72f
        val shA = if (isAttacker) lerp(20f, -100f, victoryT.coerceIn(0f, 1f)) else 15f
        val shAR = Math.toRadians(shA.toDouble())
        val shEX = shX - d * 32f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 32f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 24f * s * cos(shAR + 0.25).toFloat()
        val shHY = shEY + 24f * s * sin(shAR + 0.25).toFloat()
        drawLimbSeg(canvas, shX, shoulderY, shEX, shEY, 15f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 12f * s, fill, stroke, alpha)
        if (!isAttacker) drawSmallBuckler(canvas, shHX - d * 14f * s, shHY - 10f * s, s, fill, stroke, alpha)

        // Lance arm
        val wpX = d * torsoHW * 0.72f
        val sar = Math.toRadians(weaponAngle.toDouble())
        val wpEX = wpX + d * 36f * s * cos(sar).toFloat()
        val wpEY = shoulderY + 36f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 28f * s * cos(sar + 0.25).toFloat()
        val wpHY = wpEY + 28f * s * sin(sar + 0.25).toFloat()
        drawLimbSeg(canvas, wpX, shoulderY, wpEX, wpEY, 15f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 12f * s, fill, stroke, alpha)
        drawLance(canvas, wpHX, wpHY, weaponAngle, d, s, alpha)

        // Extra-large pauldrons
        canvas.drawOval(RectF(wpX - 14f*s, shoulderY - 14f*s, wpX + d*28f*s, shoulderY + 18f*s), fill)
        canvas.drawOval(RectF(wpX - 14f*s, shoulderY - 14f*s, wpX + d*28f*s, shoulderY + 18f*s), stroke)
        canvas.drawOval(RectF(shX - d*28f*s, shoulderY - 14f*s, shX + 14f*s, shoulderY + 18f*s), fill)
        canvas.drawOval(RectF(shX - d*28f*s, shoulderY - 14f*s, shX + 14f*s, shoulderY + 18f*s), stroke)

        // Angular great helm
        drawGreatHelm(canvas, headR, headCY, d, s, fill, stroke, alpha)
    }

    // ---- Bishop Warrior (tall mitre, crozier staff, flowing robes) ---------

    private fun drawBishopWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f
        val headR    = 28f * s
        val headCY   = -240f * s
        val torsoTop = headCY + headR + 4f * s
        val torsoBot = torsoTop + 90f * s
        val torsoHW  = 26f * s
        val shoulderY = torsoTop + 12f * s

        fill.alpha = alpha; stroke.alpha = alpha; stroke.strokeWidth = 2.5f * s

        // Flowing robes — wide bell, no legs
        val robePath = Path().apply {
            moveTo(-torsoHW, torsoTop)
            lineTo(torsoHW, torsoTop)
            lineTo(d * 50f * s, 0f)
            lineTo(-d * 50f * s, 0f)
            close()
        }
        canvas.drawPath(robePath, fill)
        canvas.drawPath(robePath, stroke)
        // Decorative horizontal robe stripes
        stroke.alpha = (alpha * 0.35f).toInt()
        val robeStep = (0f - torsoTop) / 4f
        for (i in 1..3) {
            val ry = torsoTop + i * robeStep
            val rw = torsoHW + (50f * s - torsoHW) * (i.toFloat() / 4f)
            canvas.drawLine(-rw * 0.9f, ry, rw * 0.9f, ry, stroke)
        }
        stroke.alpha = alpha

        // Off arm
        val shX = -d * torsoHW * 0.7f
        val shA = if (isAttacker) lerp(40f, -90f, victoryT.coerceIn(0f, 1f)) else 30f
        val shAR = Math.toRadians(shA.toDouble())
        val shEX = shX - d * 30f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 30f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 24f * s * cos(shAR + 0.3).toFloat()
        val shHY = shEY + 24f * s * sin(shAR + 0.3).toFloat()
        drawLimbSeg(canvas, shX, shoulderY, shEX, shEY, 12f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 10f * s, fill, stroke, alpha)

        // Crozier arm
        val wpX = d * torsoHW * 0.7f
        val sar = Math.toRadians(weaponAngle.toDouble())
        val wpEX = wpX + d * 32f * s * cos(sar).toFloat()
        val wpEY = shoulderY + 32f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 26f * s * cos(sar + 0.3).toFloat()
        val wpHY = wpEY + 26f * s * sin(sar + 0.3).toFloat()
        drawLimbSeg(canvas, wpX, shoulderY, wpEX, wpEY, 12f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 10f * s, fill, stroke, alpha)
        drawCrozier(canvas, wpHX, wpHY, weaponAngle, d, s, alpha)

        // Smaller shoulder caps (robes cover)
        canvas.drawOval(RectF(wpX - 10f*s, shoulderY - 8f*s, wpX + d*16f*s, shoulderY + 12f*s), fill)
        canvas.drawOval(RectF(wpX - 10f*s, shoulderY - 8f*s, wpX + d*16f*s, shoulderY + 12f*s), stroke)
        canvas.drawOval(RectF(shX - d*16f*s, shoulderY - 8f*s, shX + 10f*s, shoulderY + 12f*s), fill)
        canvas.drawOval(RectF(shX - d*16f*s, shoulderY - 8f*s, shX + 10f*s, shoulderY + 12f*s), stroke)

        // Mitre + face
        drawMitre(canvas, headR, headCY, s, fill, stroke, alpha)
    }

    // ---- Rook Warrior (tower helm, war axe + tall tower shield, extra-wide) -

    private fun drawRookWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f
        val headR    = 34f * s
        val headCY   = -212f * s
        val torsoTop = headCY + headR + 3f * s
        val torsoBot = torsoTop + 85f * s
        val torsoHW  = 36f * s
        val shoulderY = torsoTop + 10f * s
        val hipY  = torsoBot
        val kneeY = hipY + 48f * s

        fill.alpha = alpha; stroke.alpha = alpha; stroke.strokeWidth = 3f * s

        // Stubby wide legs
        drawLimbSeg(canvas, -d * 8f * s, hipY, -d * 14f * s, kneeY, 18f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, -d * 14f * s, kneeY, -d * 12f * s, 0f, 16f * s, fill, stroke, alpha)

        // Extra-wide breastplate
        val bodyRect = RectF(-torsoHW, torsoTop, torsoHW, torsoBot)
        canvas.drawRoundRect(bodyRect, 6f * s, 6f * s, fill)
        canvas.drawRoundRect(bodyRect, 6f * s, 6f * s, stroke)
        stroke.alpha = (alpha * 0.4f).toInt()
        canvas.drawLine(0f, torsoTop + 8f*s, 0f, torsoBot - 8f*s, stroke)
        canvas.drawLine(-torsoHW + 5f*s, torsoTop + 32f*s, torsoHW - 5f*s, torsoTop + 32f*s, stroke)
        canvas.drawLine(-torsoHW + 5f*s, torsoTop + 60f*s, torsoHW - 5f*s, torsoTop + 60f*s, stroke)
        stroke.alpha = alpha

        // Stubby front leg + wide sabaton
        val frX1 = d * 22f * s; val frX2 = d * 20f * s
        drawLimbSeg(canvas, d * 14f * s, hipY, frX1, kneeY, 18f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, frX1, kneeY, frX2, 0f, 16f * s, fill, stroke, alpha)
        canvas.drawOval(RectF(frX2 - 3f*s, -10f*s, frX2 + d*28f*s, 0f), fill)
        canvas.drawOval(RectF(frX2 - 3f*s, -10f*s, frX2 + d*28f*s, 0f), stroke)

        // Off arm (tall tower shield or raised)
        val shX = -d * torsoHW * 0.75f
        val shA = if (isAttacker) lerp(15f, -95f, victoryT.coerceIn(0f, 1f)) else 10f
        val shAR = Math.toRadians(shA.toDouble())
        val shEX = shX - d * 34f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 34f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 26f * s * cos(shAR + 0.2).toFloat()
        val shHY = shEY + 26f * s * sin(shAR + 0.2).toFloat()
        drawLimbSeg(canvas, shX, shoulderY, shEX, shEY, 18f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 16f * s, fill, stroke, alpha)
        if (!isAttacker) drawTowerShield(canvas, shHX - d * 22f * s, shHY - 14f * s, s, fill, stroke, alpha)

        // Axe arm
        val wpX = d * torsoHW * 0.75f
        val sar = Math.toRadians(weaponAngle.toDouble())
        val wpEX = wpX + d * 38f * s * cos(sar).toFloat()
        val wpEY = shoulderY + 38f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 28f * s * cos(sar + 0.2).toFloat()
        val wpHY = wpEY + 28f * s * sin(sar + 0.2).toFloat()
        drawLimbSeg(canvas, wpX, shoulderY, wpEX, wpEY, 18f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 16f * s, fill, stroke, alpha)
        drawAxe(canvas, wpHX, wpHY, weaponAngle, d, s, alpha)

        // Massive pauldrons
        canvas.drawOval(RectF(wpX - 16f*s, shoulderY - 14f*s, wpX + d*30f*s, shoulderY + 20f*s), fill)
        canvas.drawOval(RectF(wpX - 16f*s, shoulderY - 14f*s, wpX + d*30f*s, shoulderY + 20f*s), stroke)
        canvas.drawOval(RectF(shX - d*30f*s, shoulderY - 14f*s, shX + 16f*s, shoulderY + 20f*s), fill)
        canvas.drawOval(RectF(shX - d*30f*s, shoulderY - 14f*s, shX + 16f*s, shoulderY + 20f*s), stroke)

        // Tower helm
        drawTowerHelm(canvas, headR, headCY, s, fill, stroke, alpha)
    }

    // ---- Queen Warrior (open crown, rapier + parrying dagger, sleek) -------

    private fun drawQueenWarrior(
        canvas: Canvas, s: Float,
        facingRight: Boolean, weaponAngle: Float, victoryT: Float,
        fill: Paint, stroke: Paint, isAttacker: Boolean, alpha: Int
    ) {
        val d = if (facingRight) 1f else -1f
        val headR    = 29f * s
        val headCY   = -220f * s
        val torsoTop = headCY + headR + 5f * s
        val torsoBot = torsoTop + 72f * s
        val torsoHW  = 24f * s
        val shoulderY = torsoTop + 10f * s
        val hipY  = torsoBot
        val kneeY = hipY + 54f * s

        fill.alpha = alpha; stroke.alpha = alpha; stroke.strokeWidth = 2f * s

        // Slender legs
        drawLimbSeg(canvas, -d * 5f * s, hipY, -d * 9f * s, kneeY, 12f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, -d * 9f * s, kneeY, -d * 7f * s, 0f, 10f * s, fill, stroke, alpha)

        // Sleek breastplate
        val bodyRect = RectF(-torsoHW, torsoTop, torsoHW, torsoBot)
        canvas.drawRoundRect(bodyRect, 10f * s, 10f * s, fill)
        canvas.drawRoundRect(bodyRect, 10f * s, 10f * s, stroke)
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawLine(0f, torsoTop + 6f * s, 0f, torsoBot - 6f * s, stroke)
        stroke.alpha = alpha

        // Slender front leg + sabaton
        val frX1 = d * 14f * s; val frX2 = d * 12f * s
        drawLimbSeg(canvas, d * 9f * s, hipY, frX1, kneeY, 13f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, frX1, kneeY, frX2, 0f, 11f * s, fill, stroke, alpha)
        canvas.drawOval(RectF(frX2 - 2f*s, -8f*s, frX2 + d*20f*s, 0f), fill)
        canvas.drawOval(RectF(frX2 - 2f*s, -8f*s, frX2 + d*20f*s, 0f), stroke)

        // Off arm (parrying dagger or raised)
        val shX = -d * torsoHW * 0.75f
        val shA = if (isAttacker) lerp(30f, -105f, victoryT.coerceIn(0f, 1f)) else 25f
        val shAR = Math.toRadians(shA.toDouble())
        val shEX = shX - d * 30f * s * cos(shAR).toFloat()
        val shEY = shoulderY + 30f * s * sin(shAR).toFloat()
        val shHX = shEX - d * 24f * s * cos(shAR + 0.3).toFloat()
        val shHY = shEY + 24f * s * sin(shAR + 0.3).toFloat()
        drawLimbSeg(canvas, shX, shoulderY, shEX, shEY, 12f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, shEX, shEY, shHX, shHY, 10f * s, fill, stroke, alpha)
        if (!isAttacker) drawParryingDagger(canvas, shHX, shHY, shA - 30f, d, s, alpha)

        // Rapier arm
        val wpX = d * torsoHW * 0.75f
        val sar = Math.toRadians(weaponAngle.toDouble())
        val wpEX = wpX + d * 32f * s * cos(sar).toFloat()
        val wpEY = shoulderY + 32f * s * sin(sar).toFloat()
        val wpHX = wpEX + d * 26f * s * cos(sar + 0.3).toFloat()
        val wpHY = wpEY + 26f * s * sin(sar + 0.3).toFloat()
        drawLimbSeg(canvas, wpX, shoulderY, wpEX, wpEY, 12f * s, fill, stroke, alpha)
        drawLimbSeg(canvas, wpEX, wpEY, wpHX, wpHY, 10f * s, fill, stroke, alpha)
        drawRapier(canvas, wpHX, wpHY, weaponAngle, d, s, alpha)

        // Elegant pauldrons
        canvas.drawOval(RectF(wpX - 10f*s, shoulderY - 9f*s, wpX + d*18f*s, shoulderY + 12f*s), fill)
        canvas.drawOval(RectF(wpX - 10f*s, shoulderY - 9f*s, wpX + d*18f*s, shoulderY + 12f*s), stroke)
        canvas.drawOval(RectF(shX - d*18f*s, shoulderY - 9f*s, shX + 10f*s, shoulderY + 12f*s), fill)
        canvas.drawOval(RectF(shX - d*18f*s, shoulderY - 9f*s, shX + 10f*s, shoulderY + 12f*s), stroke)

        // Open crown + triple plume
        drawQueenCrown(canvas, headR, headCY, d, s, fill, stroke, alpha)
    }

    // ---- Helmet helpers ---------------------------------------------------

    /** Pawn: scarlet feather plume flowing from ball helm. */
    private fun drawScarletPlume(canvas: Canvas, headR: Float, headCY: Float, d: Float, s: Float, alpha: Int) {
        plumePaint.alpha = alpha; plumePaint.strokeWidth = 7f * s
        canvas.drawPath(Path().apply {
            moveTo(d * 8f * s, headCY - headR + 4f * s)
            cubicTo(d * 14f * s, headCY - headR - 28f * s,
                    d * 32f * s, headCY - headR - 50f * s,
                    d * 28f * s, headCY - headR - 78f * s)
        }, plumePaint)
        plumePaint.strokeWidth = 4.5f * s; plumePaint.alpha = (alpha * 0.7f).toInt()
        canvas.drawPath(Path().apply {
            moveTo(d * 6f * s, headCY - headR + 4f * s)
            cubicTo(d * 22f * s, headCY - headR - 18f * s,
                    d * 40f * s, headCY - headR - 32f * s,
                    d * 38f * s, headCY - headR - 58f * s)
        }, plumePaint)
        plumePaint.alpha = alpha
    }

    /** Pawn defender: short straight crest. */
    private fun drawCrest(canvas: Canvas, headR: Float, headCY: Float, s: Float, alpha: Int) {
        guardPaint.strokeWidth = 5f * s; guardPaint.alpha = (alpha * 0.8f).toInt()
        canvas.drawLine(-8f * s, headCY - headR + 2f * s, 8f * s, headCY - headR - 14f * s, guardPaint)
        canvas.drawLine( 8f * s, headCY - headR - 14f * s, 2f * s, headCY - headR - 26f * s, guardPaint)
        guardPaint.alpha = alpha
    }

    /** Knight: angular box-shaped great helm with narrow eye slit. */
    private fun drawGreatHelm(
        canvas: Canvas, headR: Float, headCY: Float, d: Float,
        s: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        fill.alpha = alpha; stroke.alpha = alpha
        val hw = headR * 1.1f; val hh = headR * 1.4f
        val helmRect = RectF(-hw, headCY - hh * 0.65f, hw, headCY + hh * 0.35f)
        canvas.drawRect(helmRect, fill)
        canvas.drawRect(helmRect, stroke)
        visorPaint.alpha = alpha
        canvas.drawRect(RectF(-hw * 0.55f, headCY - 6f * s, hw * 0.55f, headCY - 1f * s), visorPaint)
        stroke.alpha = (alpha * 0.6f).toInt()
        canvas.drawLine(-hw, headCY - 6f * s, hw, headCY - 6f * s, stroke)
        stroke.alpha = alpha
        visorPaint.alpha = (alpha * 0.7f).toInt()
        for (i in 0..2) canvas.drawCircle(d * hw * 0.6f, headCY + 8f * s + i * 7f * s, 2f * s, visorPaint)
        visorPaint.alpha = alpha
    }

    /** Bishop: tall pointed mitre with gold cross. */
    private fun drawMitre(
        canvas: Canvas, headR: Float, headCY: Float,
        s: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        fill.alpha = alpha; stroke.alpha = alpha
        canvas.drawCircle(0f, headCY, headR, fill)
        canvas.drawCircle(0f, headCY, headR, stroke)
        visorPaint.alpha = alpha
        canvas.drawOval(RectF(-headR * 0.4f, headCY - 6f * s, -headR * 0.1f, headCY - 1f * s), visorPaint)
        canvas.drawOval(RectF( headR * 0.1f, headCY - 6f * s,  headR * 0.4f, headCY - 1f * s), visorPaint)

        // Tall pointed mitre
        val mitreBase = headCY - headR * 0.4f
        val mitreTop  = headCY - headR * 3.5f
        val mitreHW   = headR * 0.9f
        canvas.drawPath(Path().apply {
            moveTo(-mitreHW, mitreBase); lineTo(mitreHW, mitreBase); lineTo(0f, mitreTop); close()
        }, fill)
        canvas.drawPath(Path().apply {
            moveTo(-mitreHW, mitreBase); lineTo(mitreHW, mitreBase); lineTo(0f, mitreTop); close()
        }, stroke)

        // Gold cross on mitre
        goldStroke.alpha = alpha; goldStroke.strokeWidth = 3.5f * s
        val crossCY = mitreBase + (mitreTop - mitreBase) * 0.35f
        canvas.drawLine(0f, crossCY - 18f * s, 0f, crossCY + 10f * s, goldStroke)
        canvas.drawLine(-12f * s, crossCY - 4f * s, 12f * s, crossCY - 4f * s, goldStroke)
    }

    /** Rook: rectangular tower helm with 3 battlements and arrow slit. */
    private fun drawTowerHelm(
        canvas: Canvas, headR: Float, headCY: Float,
        s: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        fill.alpha = alpha; stroke.alpha = alpha
        val hw = headR * 1.2f
        val helmTop = headCY - headR * 0.9f
        val helmBot = headCY + headR * 0.5f
        canvas.drawRect(RectF(-hw, helmTop, hw, helmBot), fill)
        canvas.drawRect(RectF(-hw, helmTop, hw, helmBot), stroke)

        val battleH = headR * 0.5f
        val battleW = hw * 0.52f
        for (bi in -1..1) {
            val bx = bi * hw * 0.68f
            canvas.drawRect(RectF(bx - battleW * 0.42f, helmTop - battleH, bx + battleW * 0.42f, helmTop), fill)
            canvas.drawRect(RectF(bx - battleW * 0.42f, helmTop - battleH, bx + battleW * 0.42f, helmTop), stroke)
        }
        visorPaint.alpha = alpha
        canvas.drawRect(RectF(-3f * s, headCY - headR * 0.3f, 3f * s, headCY + 5f * s), visorPaint)
    }

    /** Queen: open crown with 5 jewelled spikes + triple-colour plume. */
    private fun drawQueenCrown(
        canvas: Canvas, headR: Float, headCY: Float, d: Float,
        s: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        fill.alpha = alpha; stroke.alpha = alpha
        canvas.drawCircle(0f, headCY, headR, fill)
        canvas.drawCircle(0f, headCY, headR, stroke)
        visorPaint.alpha = alpha
        canvas.drawRoundRect(RectF(-headR * 0.44f, headCY - 7f * s, headR * 0.44f, headCY + 3f * s), 2f * s, 2f * s, visorPaint)

        // Crown band
        val crownBase = headCY - headR * 0.5f
        val bandH = headR * 0.4f
        canvas.drawRect(RectF(-headR * 1.1f, crownBase - bandH, headR * 1.1f, crownBase), fill)
        canvas.drawRect(RectF(-headR * 1.1f, crownBase - bandH, headR * 1.1f, crownBase), stroke)

        // 5 spikes alternating tall/short
        val spikeXs = floatArrayOf(-headR * 1.0f, -headR * 0.5f, 0f, headR * 0.5f, headR * 1.0f)
        val spikeHt = floatArrayOf(0.8f, 1.3f, 1.8f, 1.3f, 0.8f)
        stroke.strokeWidth = 4f * s
        for (si in spikeXs.indices) {
            val sx = spikeXs[si]
            val sy = crownBase - bandH
            val spikeTop = sy - spikeHt[si] * headR * 0.8f
            canvas.drawLine(sx, sy, sx, spikeTop, stroke)
            goldFill.alpha = alpha; stroke.strokeWidth = 1.5f * s
            canvas.drawCircle(sx, spikeTop, 4.5f * s, goldFill)
            canvas.drawCircle(sx, spikeTop, 4.5f * s, stroke)
            stroke.strokeWidth = 2f * s
        }

        // Triple-colour plume (red + purple + gold)
        val pbX = d * headR * 0.8f; val pbY = crownBase - bandH
        plumePaint.alpha = alpha; plumePaint.strokeWidth = 5f * s
        canvas.drawPath(Path().apply {
            moveTo(pbX, pbY)
            cubicTo(pbX + d * 10f * s, pbY - 30f * s, pbX + d * 24f * s, pbY - 50f * s, pbX + d * 20f * s, pbY - 72f * s)
        }, plumePaint)
        purplePaint.alpha = alpha; purplePaint.strokeWidth = 4f * s
        canvas.drawPath(Path().apply {
            moveTo(pbX, pbY)
            cubicTo(pbX + d * 18f * s, pbY - 20f * s, pbX + d * 36f * s, pbY - 36f * s, pbX + d * 34f * s, pbY - 58f * s)
        }, purplePaint)
        goldStroke.alpha = (alpha * 0.9f).toInt(); goldStroke.strokeWidth = 3.5f * s
        canvas.drawPath(Path().apply {
            moveTo(pbX, pbY)
            cubicTo(pbX + d * 6f * s, pbY - 24f * s, pbX + d * 14f * s, pbY - 44f * s, pbX + d * 10f * s, pbY - 66f * s)
        }, goldStroke)
    }

    // ---- Weapon helpers ---------------------------------------------------

    /** Pawn: classic sword — tapered blade, cross-guard, leather handle. */
    private fun drawSword(canvas: Canvas, hx: Float, hy: Float,
                           angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;       val perpY = cosA
        val bw = 5f * s

        val tipX = hx + d * 68f * s * cosA; val tipY = hy + 68f * s * sinA
        bladeFill.alpha = alpha; bladeStroke.alpha = alpha; bladeStroke.strokeWidth = 1.5f * s
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * bw, hy + perpY * bw)
            lineTo(hx - perpX * bw, hy - perpY * bw)
            lineTo(tipX, tipY); close()
        }, bladeFill)
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * bw, hy + perpY * bw)
            lineTo(hx - perpX * bw, hy - perpY * bw)
            lineTo(tipX, tipY); close()
        }, bladeStroke)

        guardPaint.strokeWidth = 4f * s; guardPaint.alpha = alpha
        canvas.drawLine(hx + perpX * 15f * s, hy + perpY * 15f * s,
                        hx - perpX * 15f * s, hy - perpY * 15f * s, guardPaint)

        val hEndX = hx - d * 22f * s * cosA; val hEndY = hy - 22f * s * sinA
        handlePaint.strokeWidth = 7f * s; handlePaint.alpha = alpha
        canvas.drawLine(hx, hy, hEndX, hEndY, handlePaint)
        bladeFill.alpha = alpha
        canvas.drawCircle(hEndX, hEndY, 5f * s, bladeFill)
        canvas.drawCircle(hEndX, hEndY, 5f * s, guardPaint)
    }

    /** Knight: long lance — pole + diamond spear tip + grip wrapping. */
    private fun drawLance(canvas: Canvas, hx: Float, hy: Float,
                           angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;       val perpY = cosA

        handlePaint.strokeWidth = 5f * s; handlePaint.alpha = alpha
        val poleLen = 90f * s
        val tipX = hx + d * poleLen * cosA; val tipY = hy + poleLen * sinA
        canvas.drawLine(hx, hy, tipX, tipY, handlePaint)

        // Diamond tip
        val tw = 6f * s; val tl = 20f * s
        bladeFill.alpha = alpha; bladeStroke.alpha = alpha; bladeStroke.strokeWidth = 1.5f * s
        canvas.drawPath(Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX + perpX * tw, tipY + perpY * tw)
            lineTo(tipX + d * tl * cosA, tipY + tl * sinA)
            lineTo(tipX - perpX * tw, tipY - perpY * tw); close()
        }, bladeFill)
        canvas.drawPath(Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX + perpX * tw, tipY + perpY * tw)
            lineTo(tipX + d * tl * cosA, tipY + tl * sinA)
            lineTo(tipX - perpX * tw, tipY - perpY * tw); close()
        }, bladeStroke)

        // Blue grip wrapping
        bluePaint.strokeWidth = 4f * s; bluePaint.alpha = alpha
        for (i in 0..2) {
            val gx0 = hx + d * (i * 8f + 4f) * s * cosA - perpX * 5f * s
            val gy0 = hy + (i * 8f + 4f) * s * sinA - perpY * 5f * s
            canvas.drawLine(gx0, gy0, gx0 + perpX * 10f * s, gy0 + perpY * 10f * s, bluePaint)
        }
    }

    /** Bishop: golden crozier staff with curved crook and jewel. */
    private fun drawCrozier(canvas: Canvas, hx: Float, hy: Float,
                             angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()

        goldStroke.strokeWidth = 5f * s; goldStroke.alpha = alpha
        val topX = hx + d * 80f * s * cosA; val topY = hy + 80f * s * sinA
        canvas.drawLine(hx, hy, topX, topY, goldStroke)

        val crookR = 14f * s
        goldStroke.strokeWidth = 4f * s
        canvas.drawArc(RectF(topX - crookR, topY - crookR * 2.2f, topX + crookR, topY),
            90f, -200f, false, goldStroke)
        goldFill.alpha = alpha
        canvas.drawCircle(topX, topY - crookR * 1.1f, 5f * s, goldFill)
    }

    /** Rook: war axe — handle + crescent blade. */
    private fun drawAxe(canvas: Canvas, hx: Float, hy: Float,
                         angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;       val perpY = cosA

        handlePaint.strokeWidth = 9f * s; handlePaint.alpha = alpha
        val handleLen = 60f * s
        val axeX = hx + d * handleLen * cosA; val axeY = hy + handleLen * sinA
        canvas.drawLine(hx, hy, axeX, axeY, handlePaint)

        val bladeW = 28f * s
        bladeFill.alpha = alpha; bladeStroke.alpha = alpha; bladeStroke.strokeWidth = 2f * s
        canvas.drawPath(Path().apply {
            moveTo(axeX + perpX * bladeW, axeY + perpY * bladeW)
            cubicTo(axeX + perpX * bladeW + d * 30f * s * cosA, axeY + perpY * bladeW + 30f * s * sinA,
                    axeX - perpX * bladeW + d * 30f * s * cosA, axeY - perpY * bladeW + 30f * s * sinA,
                    axeX - perpX * bladeW, axeY - perpY * bladeW)
            cubicTo(axeX - perpX * bladeW * 0.4f + d * 14f * s * cosA, axeY - perpY * bladeW * 0.4f + 14f * s * sinA,
                    axeX + perpX * bladeW * 0.4f + d * 14f * s * cosA, axeY + perpY * bladeW * 0.4f + 14f * s * sinA,
                    axeX + perpX * bladeW, axeY + perpY * bladeW)
            close()
        }, bladeFill)
        canvas.drawPath(Path().apply {
            moveTo(axeX + perpX * bladeW, axeY + perpY * bladeW)
            cubicTo(axeX + perpX * bladeW + d * 30f * s * cosA, axeY + perpY * bladeW + 30f * s * sinA,
                    axeX - perpX * bladeW + d * 30f * s * cosA, axeY - perpY * bladeW + 30f * s * sinA,
                    axeX - perpX * bladeW, axeY - perpY * bladeW)
            cubicTo(axeX - perpX * bladeW * 0.4f + d * 14f * s * cosA, axeY - perpY * bladeW * 0.4f + 14f * s * sinA,
                    axeX + perpX * bladeW * 0.4f + d * 14f * s * cosA, axeY + perpY * bladeW * 0.4f + 14f * s * sinA,
                    axeX + perpX * bladeW, axeY + perpY * bladeW)
            close()
        }, bladeStroke)
    }

    /** Queen: rapier — very long thin blade, swept guard, gold pommel. */
    private fun drawRapier(canvas: Canvas, hx: Float, hy: Float,
                            angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;       val perpY = cosA
        val bw = 3f * s

        val tipX = hx + d * 88f * s * cosA; val tipY = hy + 88f * s * sinA
        bladeFill.alpha = alpha; bladeStroke.alpha = alpha; bladeStroke.strokeWidth = 1f * s
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * bw, hy + perpY * bw)
            lineTo(hx - perpX * bw, hy - perpY * bw)
            lineTo(tipX, tipY); close()
        }, bladeFill)
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * bw, hy + perpY * bw)
            lineTo(hx - perpX * bw, hy - perpY * bw)
            lineTo(tipX, tipY); close()
        }, bladeStroke)

        guardPaint.strokeWidth = 3f * s; guardPaint.alpha = alpha
        val gLen = 18f * s
        canvas.drawLine(hx + perpX * gLen, hy + perpY * gLen, hx - perpX * gLen, hy - perpY * gLen, guardPaint)
        canvas.drawLine(hx - perpX * gLen, hy - perpY * gLen,
                        hx - perpX * gLen + d * 10f * s * cosA, hy - perpY * gLen + 10f * s * sinA, guardPaint)

        val hEndX = hx - d * 20f * s * cosA; val hEndY = hy - 20f * s * sinA
        handlePaint.strokeWidth = 5f * s; handlePaint.alpha = alpha
        canvas.drawLine(hx, hy, hEndX, hEndY, handlePaint)
        goldFill.alpha = alpha
        canvas.drawCircle(hEndX, hEndY, 4f * s, goldFill)
    }

    // ---- Off-hand weapon helpers ------------------------------------------

    /** Pawn defender: round shield with inner ring and boss. */
    private fun drawRoundShield(canvas: Canvas, cx: Float, cy: Float, s: Float,
                                 fill: Paint, stroke: Paint, alpha: Int) {
        fill.alpha = alpha; stroke.alpha = alpha
        val r = RectF(cx - 22f * s, cy - 28f * s, cx + 22f * s, cy + 28f * s)
        canvas.drawOval(r, fill); canvas.drawOval(r, stroke)
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawOval(RectF(cx - 16f * s, cy - 20f * s, cx + 16f * s, cy + 20f * s), stroke)
        stroke.alpha = alpha
        canvas.drawCircle(cx, cy, 5.5f * s, stroke)
    }

    /** Knight: small round buckler. */
    private fun drawSmallBuckler(canvas: Canvas, cx: Float, cy: Float, s: Float,
                                  fill: Paint, stroke: Paint, alpha: Int) {
        val r = 16f * s
        fill.alpha = alpha; stroke.alpha = alpha
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawCircle(cx, cy, r * 0.6f, stroke)
        stroke.alpha = alpha
        canvas.drawCircle(cx, cy, 4f * s, stroke)
    }

    /** Rook: tall rectangular tower shield with cross. */
    private fun drawTowerShield(canvas: Canvas, cx: Float, cy: Float, s: Float,
                                 fill: Paint, stroke: Paint, alpha: Int) {
        fill.alpha = alpha; stroke.alpha = alpha
        val sw = 16f * s; val sh = 40f * s
        canvas.drawPath(Path().apply {
            addRoundRect(RectF(cx - sw, cy - sh, cx + sw, cy + sh * 0.6f), 4f * s, 4f * s, Path.Direction.CW)
        }, fill)
        canvas.drawPath(Path().apply {
            addRoundRect(RectF(cx - sw, cy - sh, cx + sw, cy + sh * 0.6f), 4f * s, 4f * s, Path.Direction.CW)
        }, stroke)
        stroke.alpha = (alpha * 0.5f).toInt()
        canvas.drawLine(cx, cy - sh * 0.7f, cx, cy + sh * 0.4f, stroke)
        canvas.drawLine(cx - sw * 0.7f, cy - sh * 0.2f, cx + sw * 0.7f, cy - sh * 0.2f, stroke)
        stroke.alpha = alpha
    }

    /** Queen: small parrying dagger in off hand. */
    private fun drawParryingDagger(canvas: Canvas, hx: Float, hy: Float,
                                    angle: Float, d: Float, s: Float, alpha: Int) {
        val a = Math.toRadians(angle.toDouble())
        val cosA = cos(a).toFloat(); val sinA = sin(a).toFloat()
        val perpX = -sinA * d;       val perpY = cosA

        val tipX = hx + d * 32f * s * cosA; val tipY = hy + 32f * s * sinA
        bladeFill.alpha = alpha; bladeStroke.alpha = alpha; bladeStroke.strokeWidth = 1f * s
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * 2.5f * s, hy + perpY * 2.5f * s)
            lineTo(hx - perpX * 2.5f * s, hy - perpY * 2.5f * s)
            lineTo(tipX, tipY); close()
        }, bladeFill)
        canvas.drawPath(Path().apply {
            moveTo(hx + perpX * 2.5f * s, hy + perpY * 2.5f * s)
            lineTo(hx - perpX * 2.5f * s, hy - perpY * 2.5f * s)
            lineTo(tipX, tipY); close()
        }, bladeStroke)
        guardPaint.strokeWidth = 2.5f * s; guardPaint.alpha = alpha
        canvas.drawLine(hx + perpX * 8f * s, hy + perpY * 8f * s,
                        hx - perpX * 8f * s, hy - perpY * 8f * s, guardPaint)
    }

    // ---- Sparks -----------------------------------------------------------

    private fun sparkColors(type: PieceType): IntArray = when (type) {
        PieceType.PAWN   -> intArrayOf(Color.rgb(255, 200, 30), Color.rgb(255, 120, 20), Color.rgb(255, 240, 150))
        PieceType.KNIGHT -> intArrayOf(Color.rgb(80, 160, 255), Color.rgb(180, 220, 255), Color.rgb(40, 80, 200))
        PieceType.BISHOP -> intArrayOf(Color.rgb(212, 175, 55), Color.rgb(255, 230, 100), Color.rgb(255, 255, 220))
        PieceType.ROOK   -> intArrayOf(Color.rgb(255, 100, 20), Color.rgb(255, 50, 0), Color.rgb(255, 180, 60))
        PieceType.QUEEN  -> intArrayOf(Color.rgb(180, 0, 240), Color.rgb(220, 170, 255), Color.rgb(255, 215, 0))
        PieceType.KING   -> intArrayOf(Color.rgb(255, 215, 0), Color.rgb(255, 255, 255), Color.rgb(255, 180, 0))
    }

    private fun drawSparks(
        canvas: Canvas, cx: Float, cy: Float,
        intensity: Float, s: Float, angleOffset: Float, count: Int, type: PieceType
    ) {
        if (intensity < 0.01f) return
        sparkPaint.strokeWidth = 2.5f * s
        val colors = sparkColors(type)
        val step = 360f / count
        val inner = 10f * s
        repeat(count) { i ->
            val a     = Math.toRadians(((i * step) + angleOffset).toDouble())
            val outer = (if (i % 2 == 0) 42f else 28f) * s * intensity
            val a2    = (255 * intensity).toInt()
            val c     = colors[i % colors.size]
            sparkPaint.color = Color.argb(a2, Color.red(c), Color.green(c), Color.blue(c))
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(),
                sparkPaint
            )
        }
    }

    // ---- Shared limb helper -----------------------------------------------

    private fun drawLimbSeg(
        canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        thick: Float, fill: Paint, stroke: Paint, alpha: Int
    ) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.5f) return
        val ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        canvas.save()
        canvas.translate((x1 + x2) / 2f, (y1 + y2) / 2f)
        canvas.rotate(ang)
        val hw = len / 2f + thick * 0.28f
        fill.alpha = alpha; stroke.alpha = alpha
        canvas.drawOval(RectF(-hw, -thick / 2f, hw, thick / 2f), fill)
        canvas.drawOval(RectF(-hw, -thick / 2f, hw, thick / 2f), stroke)
        canvas.restore()
    }

    // ---- Math helpers -----------------------------------------------------

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
