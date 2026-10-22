package com.altinarama.harita

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Uygulama güncellendikten sonra uygulamayı yeniden başlatır.
 *
 * Android, bir paket güncellendiğinde yalnızca kendisine
 * ACTION_MY_PACKAGE_REPLACED bildirimi gönderir.
 */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.PACKAGE_REPLACED"
        ) {
            Log.i("UpdateReceiver", "Paket guncellendi, uygulama yeniden baslatiliyor")
            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launch)
            }
        }
    }

    companion object {
        private const val TAG = "UpdateReceiver"
    }
}
