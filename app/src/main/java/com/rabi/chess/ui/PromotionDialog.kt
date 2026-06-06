package com.rabi.chess.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rabi.chess.R
import com.rabi.chess.engine.PieceColor
import com.rabi.chess.engine.PieceType

/** Asks the player which piece a promoting pawn becomes. */
object PromotionDialog {

    fun show(context: Context, color: PieceColor, onChosen: (PieceType) -> Unit) {
        val types = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        val glyphs = if (color == PieceColor.WHITE)
            listOf("♕", "♖", "♗", "♘") else listOf("♛", "♜", "♝", "♞")
        val labels = listOf(
            context.getString(R.string.queen),
            context.getString(R.string.rook),
            context.getString(R.string.bishop),
            context.getString(R.string.knight)
        ).mapIndexed { i, s -> "${glyphs[i]}   $s" }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.promote_title)
            .setCancelable(false)
            .setItems(labels) { _, which -> onChosen(types[which]) }
            .show()
    }
}
