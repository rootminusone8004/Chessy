package com.mhrahmankwoshik.chessy.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

object TermuxUtils {
    const val PACKAGE_TERMUX = "com.termux"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
    const val INSTALL_COMMAND = "pkg install -y stockfish socat"
    const val SETUP_COMMAND = "pkg install -y stockfish socat"
    const val CONFIG_COMMAND = "mkdir -p ~/.termux && echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"
    const val SOCKET_COMMAND = "socat TCP-LISTEN:2222,reuseaddr,fork EXEC:stockfish"
    const val STORAGE_COMMAND = "termux-setup-storage && cp \$(which stockfish) ~/storage/shared/"
    const val FDROID_TERMUX_URL = "https://f-droid.org/packages/com.termux/"

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(PACKAGE_TERMUX, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(PACKAGE_TERMUX, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppPermissionSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "In Settings: Tap 'Permissions' -> 'Additional permissions' -> 'Run commands in Termux environment' -> Allow",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openOrInstallTermux(context: Context) {
        if (isTermuxInstalled(context)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_TERMUX)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_TERMUX_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openTermux(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_TERMUX)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            openOrInstallTermux(context)
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
