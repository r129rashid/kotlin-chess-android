package com.rabi.chess

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.rabi.chess.ai.Difficulty
import com.rabi.chess.databinding.ActivityMainBinding
import com.rabi.chess.engine.PieceColor

/** Start menu: choose local two-player, or configure a game against the computer. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sensible defaults.
        binding.difficultyGroup.check(binding.diffMedium.id)
        binding.colorGroup.check(binding.colorWhite.id)

        binding.btnTwoPlayer.setOnClickListener {
            launchGame(GameActivity.MODE_TWO_PLAYER, Difficulty.MEDIUM, PieceColor.WHITE)
        }

        binding.btnComputer.setOnClickListener {
            binding.configCard.visibility =
                if (binding.configCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.btnStart.setOnClickListener {
            val difficulty = when (binding.difficultyGroup.checkedButtonId) {
                binding.diffEasy.id -> Difficulty.EASY
                binding.diffHard.id -> Difficulty.HARD
                else -> Difficulty.MEDIUM
            }
            val color = if (binding.colorGroup.checkedButtonId == binding.colorBlack.id)
                PieceColor.BLACK else PieceColor.WHITE
            launchGame(GameActivity.MODE_COMPUTER, difficulty, color)
        }
    }

    private fun launchGame(mode: String, difficulty: Difficulty, humanColor: PieceColor) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MODE, mode)
            putExtra(GameActivity.EXTRA_DIFFICULTY, difficulty.name)
            putExtra(GameActivity.EXTRA_HUMAN_COLOR, humanColor.name)
        })
    }
}
