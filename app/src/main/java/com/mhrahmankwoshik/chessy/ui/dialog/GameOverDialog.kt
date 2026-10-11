package com.mhrahmankwoshik.chessy.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import com.mhrahmankwoshik.chessy.R
import com.mhrahmankwoshik.chessy.databinding.DialogGameOverBinding

class GameOverDialog(
    context: Context,
    private val message: String,
    private val onCopyPgn: () -> Unit,
    private val onNewGame: () -> Unit,
    private val onReviewBoard: (() -> Unit)? = null
) : Dialog(context) {

    private lateinit var binding: DialogGameOverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure dialog window has proper dimensions (88% width, max 400dp) so buttons don't clip
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val screenWidth = context.resources.displayMetrics.widthPixels
            val density = context.resources.displayMetrics.density
            val maxAllowedWidth = (400 * density).toInt()
            val targetWidth = (screenWidth * 0.88).toInt().coerceAtMost(maxAllowedWidth)
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        setCanceledOnTouchOutside(true)
        setCancelable(true)

        binding.tvGameOverMessage.text = message

        // Set contextual icon based on game outcome
        if (message.contains("resign", ignoreCase = true)) {
            binding.ivGameOverIcon.setImageResource(R.drawable.ic_flag)
        } else if (message.contains("won", ignoreCase = true)) {
            binding.ivGameOverIcon.setImageResource(R.drawable.ic_chess_wk)
        } else {
            binding.ivGameOverIcon.setImageResource(R.drawable.ic_chess_bk)
        }

        // Primary: New Game
        binding.btnGameOverNewGame.setOnClickListener {
            dismiss()
            onNewGame()
        }

        // Secondary: Review Board (Dismisses prompt so player can explore board)
        binding.btnDismissDialog.setOnClickListener {
            dismiss()
            onReviewBoard?.invoke()
        }

        // Secondary: Copy PGN (copies with visual feedback)
        binding.btnCopyPgn.setOnClickListener {
            onCopyPgn()
            binding.btnCopyPgn.text = "Copied!"
            binding.btnCopyPgn.postDelayed({
                binding.btnCopyPgn.text = context.getString(R.string.action_copy_pgn)
            }, 1500)
        }
    }
}
