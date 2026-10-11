package com.mhrahmankwoshik.chessy.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val stdout = intent.getStringExtra("stdout") ?: ""
        val stderr = intent.getStringExtra("stderr") ?: ""
        val exitCode = intent.getIntExtra("exitCode", -1)
        val errCode = intent.getIntExtra("errCode", 0)
        val errmsg = intent.getStringExtra("errmsg") ?: ""

        Log.d(TAG, "Termux response: exitCode=$exitCode, stdout=${stdout.take(100)}, stderr=$stderr, errmsg=$errmsg")

        listener?.invoke(stdout, stderr, exitCode)
    }

    companion object {
        private const val TAG = "TermuxResultReceiver"
        const val ACTION_TERMUX_RESULT = "com.mhrahmankwoshik.chessy.TERMUX_RESULT"

        var listener: ((stdout: String, stderr: String, exitCode: Int) -> Unit)? = null
    }
}
