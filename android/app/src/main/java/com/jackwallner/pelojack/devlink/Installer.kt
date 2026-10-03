package com.jackwallner.pelojack.devlink

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val RESULT_ACTION = "com.jackwallner.pelojack.INSTALL_RESULT"

/**
 * Installs APKs (one, or a base plus splits) through Android's package installer. As device owner
 * this needs no tap on the tablet; otherwise Android shows its install prompt.
 */
class Installer(private val context: Context) {
    private val installer get() = context.packageManager.packageInstaller

    fun begin(packageName: String?): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        packageName?.takeIf { it.isNotBlank() }?.let(params::setAppPackageName)
        return installer.createSession(params)
    }

    fun add(session: Int, name: String, apk: InputStream, length: Long) {
        installer.openSession(session).use { open ->
            open.openWrite(name, 0, length).use { out ->
                apk.copyTo(out)
                open.fsync(out)
            }
        }
    }

    /** Commits and waits for Android's answer. Returns "success" or the failure message. */
    fun commit(session: Int, timeoutSeconds: Long = 180): String {
        val done = CountDownLatch(1)
        var result = "timed out"
        val action = "$RESULT_ACTION.$session"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        // Not device owner: hand Android's install prompt to whoever is on the bike.
                        @Suppress("DEPRECATION")
                        (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        result = "waiting for a tap on the tablet"
                        return
                    }
                    PackageInstaller.STATUS_SUCCESS -> result = "success"
                    else -> result = "failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}"
                }
                done.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(context, session, Intent(action).setPackage(context.packageName), flags)
            installer.openSession(session).use { it.commit(callback.intentSender) }
            done.await(timeoutSeconds, TimeUnit.SECONDS)
        } finally {
            context.unregisterReceiver(receiver)
        }
        return result
    }

    /** Commits without waiting, for Pelojack updating itself: the install ends this process. */
    fun commitAndForget(session: Int) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, session, Intent(RESULT_ACTION).setPackage(context.packageName), flags)
        installer.openSession(session).use { it.commit(callback.intentSender) }
    }

    fun abandon(session: Int) = runCatching { installer.abandonSession(session) }
}
