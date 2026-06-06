package com.rabi.chess

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rabi.chess.ads.AdManager
import com.rabi.chess.ai.ChessAI
import com.rabi.chess.ai.Difficulty
import com.rabi.chess.audio.Sfx
import com.rabi.chess.audio.SoundManager
import com.rabi.chess.databinding.ActivityGameBinding
import com.rabi.chess.engine.Board
import com.rabi.chess.engine.GameResult
import com.rabi.chess.engine.Move
import com.rabi.chess.engine.MoveGenerator
import com.rabi.chess.engine.PieceColor
import com.rabi.chess.engine.PieceType
import com.rabi.chess.engine.Position
import com.rabi.chess.ui.PromotionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Drives a single game: input, animation, sound, AI replies, and game-over handling. */
class GameActivity : AppCompatActivity(), com.rabi.chess.ui.BoardView.Listener {

    companion object {
        const val EXTRA_MODE        = "mode"
        const val EXTRA_DIFFICULTY  = "difficulty"
        const val EXTRA_HUMAN_COLOR = "human_color"
        const val MODE_TWO_PLAYER   = "TWO_PLAYER"
        const val MODE_COMPUTER     = "COMPUTER"
        private const val PREFS     = "chess"
        private const val KEY_MUTED = "muted"
    }

    private lateinit var binding: ActivityGameBinding
    private lateinit var sound:   SoundManager
    private lateinit var ads:     AdManager

    private var board       = Board.initial()
    private var lastMove:   Move? = null
    private var gameOver    = false

    private var vsComputer  = false
    private var difficulty  = Difficulty.MEDIUM
    private var humanColor  = PieceColor.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vsComputer = intent.getStringExtra(EXTRA_MODE) == MODE_COMPUTER
        difficulty = runCatching {
            Difficulty.valueOf(intent.getStringExtra(EXTRA_DIFFICULTY) ?: "MEDIUM")
        }.getOrDefault(Difficulty.MEDIUM)
        humanColor = runCatching {
            PieceColor.valueOf(intent.getStringExtra(EXTRA_HUMAN_COLOR) ?: "WHITE")
        }.getOrDefault(PieceColor.WHITE)

        sound      = SoundManager(this)
        sound.muted = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MUTED, false)
        updateMuteIcon()

        ads = AdManager(applicationContext)
        ads.initialize()

        binding.boardView.listener    = this
        binding.boardView.whiteBottom = !(vsComputer && humanColor == PieceColor.BLACK)

        binding.btnUndo.setOnClickListener      { onUndo() }
        binding.btnNewGame.setOnClickListener   { startNewGameWithAd() }
        binding.btnResign.setOnClickListener    { onResign() }
        binding.btnPlayAgain.setOnClickListener { startNewGameWithAd() }
        binding.btnMenu.setOnClickListener      { finish() }
        binding.btnMute.setOnClickListener      { toggleMute() }

        resetGame()
    }

    // ---- Game flow --------------------------------------------------------

    private fun resetGame() {
        // Cancel any in-progress animations before resetting state
        binding.pawnBattleView.cancel()
        binding.boardView.cancelAnimations()

        board    = Board.initial()
        lastMove = null
        gameOver = false
        binding.resultCard.visibility = View.GONE
        binding.boardView.render(board, null)
        updateStatus()
        if (vsComputer && board.sideToMove != humanColor) {
            triggerAi()
        } else {
            binding.boardView.inputEnabled = true
        }
    }

    override fun onMoveChosen(move: Move) {
        if (gameOver) return
        commitMove(move)
    }

    override fun onPromotionChosen(from: Position, to: Position) {
        if (gameOver) return
        PromotionDialog.show(this, board.sideToMove) { type ->
            commitMove(Move(from, to, promotion = type))
        }
    }

    private fun commitMove(move: Move) {
        val movingPiece   = board.pieceAt(move.from) ?: return

        // Capture these BEFORE makeMove removes pieces from the board
        val capturedPiece = board.pieceAt(move.to)
        val isCapture     = capturedPiece != null || move.isEnPassant
        // Pawn battle fires whenever a pawn takes a pawn (including en passant, always pawn-on-pawn)
        val isPawnBattle  = movingPiece.type == PieceType.PAWN &&
                            (capturedPiece?.type == PieceType.PAWN || move.isEnPassant)
        val isPromotion   = move.promotion != null

        board.makeMove(move)
        lastMove = move
        binding.boardView.inputEnabled = false

        val result         = MoveGenerator.result(board)
        val opponentInCheck = MoveGenerator.isInCheck(board, board.sideToMove)

        val sfx = when {
            result.isGameOver  -> Sfx.GAME_OVER
            opponentInCheck    -> Sfx.CHECK
            isCapture          -> Sfx.CAPTURE
            else               -> Sfx.MOVE
        }
        sound.play(sfx)

        binding.boardView.animateMove(move, movingPiece) {
            // After the piece glides, render the new position (queen appears, check shows, etc.)
            binding.boardView.render(board, lastMove)
            updateStatus()

            // --- Animation sequencing ---
            // We compose: pawn-battle → promotion-glow → game-continue
            // Each step only fires if the condition applies; otherwise skips to the next.

            fun continueGame() {
                when {
                    result.isGameOver                              -> showResult(result)
                    vsComputer && board.sideToMove != humanColor  -> triggerAi()
                    else                                           -> binding.boardView.inputEnabled = true
                }
            }

            fun afterPawnBattle() {
                // Animation 3: promotion glow (only if this was also a promotion)
                if (isPromotion) {
                    binding.boardView.showPromotionGlow(move.to) { continueGame() }
                } else {
                    continueGame()
                }
            }

            // Animation — pawn battle cutscene
            if (isPawnBattle) {
                binding.pawnBattleView.show(movingPiece.color) { afterPawnBattle() }
            } else {
                afterPawnBattle()
            }
        }
    }

    private fun triggerAi() {
        binding.boardView.inputEnabled = false
        binding.progressThinking.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.thinking)
        lifecycleScope.launch {
            val move = withContext(Dispatchers.Default) { ChessAI.bestMove(board, difficulty) }
            binding.progressThinking.visibility = View.GONE
            if (move != null && !gameOver) {
                commitMove(move)
            } else {
                updateStatus()
            }
        }
    }

    private fun onUndo() {
        if (binding.progressThinking.visibility == View.VISIBLE) return
        val plies = if (vsComputer) 2 else 1
        var undone = 0
        repeat(plies) { if (board.moveCount > 0) { board.undoMove(); undone++ } }
        if (undone == 0) return
        gameOver = false
        binding.resultCard.visibility = View.GONE
        lastMove = null
        binding.boardView.cancelAnimations()
        binding.boardView.render(board, null)
        updateStatus()
        binding.boardView.inputEnabled = true
    }

    private fun onResign() {
        if (gameOver) return
        val loser = board.sideToMove
        gameOver  = true
        binding.boardView.inputEnabled = false
        sound.play(Sfx.GAME_OVER)
        val msg = if (vsComputer) {
            if (loser == humanColor) getString(R.string.you_lose) else getString(R.string.you_win)
        } else {
            if (loser == PieceColor.WHITE) getString(R.string.black_wins) else getString(R.string.white_wins)
        }
        binding.tvResult.text           = msg
        binding.resultCard.visibility   = View.VISIBLE
    }

    private fun showResult(result: GameResult) {
        gameOver = true
        binding.boardView.inputEnabled = false
        binding.tvResult.text = resultText(result)
        // Animation 4: checkmate curtain — king tips, dark drape falls, then card appears
        if (result == GameResult.CHECKMATE) {
            binding.boardView.playCheckmateCurtain {
                binding.resultCard.visibility = View.VISIBLE
            }
        } else {
            binding.resultCard.visibility = View.VISIBLE
        }
    }

    private fun resultText(result: GameResult): String = when (result) {
        GameResult.CHECKMATE -> {
            val winner = board.sideToMove.opposite()
            if (vsComputer) {
                if (winner == humanColor) getString(R.string.you_win) else getString(R.string.you_lose)
            } else {
                if (winner == PieceColor.WHITE) getString(R.string.white_wins) else getString(R.string.black_wins)
            }
        }
        GameResult.STALEMATE             -> getString(R.string.stalemate)
        GameResult.DRAW_FIFTY_MOVE       -> getString(R.string.draw_fifty)
        GameResult.DRAW_INSUFFICIENT_MATERIAL -> getString(R.string.draw_material)
        GameResult.ONGOING               -> ""
    }

    private fun startNewGameWithAd() {
        ads.onGameOver(this) { resetGame() }
    }

    private fun updateStatus() {
        if (gameOver) return
        val base  = if (board.sideToMove == PieceColor.WHITE) getString(R.string.white_to_move)
                    else getString(R.string.black_to_move)
        val check = if (MoveGenerator.isInCheck(board, board.sideToMove))
                    "  ·  ${getString(R.string.in_check)}" else ""
        binding.tvStatus.text = base + check
    }

    private fun toggleMute() {
        sound.muted = !sound.muted
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_MUTED, sound.muted).apply()
        updateMuteIcon()
    }

    private fun updateMuteIcon() {
        val res = if (sound.muted) android.R.drawable.ic_lock_silent_mode
                  else android.R.drawable.ic_lock_silent_mode_off
        binding.btnMute.setIconResource(res)
    }

    override fun onDestroy() {
        sound.release()
        super.onDestroy()
    }
}
