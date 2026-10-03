package com.jackwallner.pelojack.devlink

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.jackwallner.pelojack.MainActivity

/**
 * Made device owner by the opt-in `scripts/bike-link.sh`, which lets Pelojack install APKs without
 * a prompt and keeps Pelojack as the bike's home screen.
 */
class OwnerReceiver : DeviceAdminReceiver()

object Owner {
    fun component(context: Context) = ComponentName(context, OwnerReceiver::class.java)

    fun isActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    /** Android can clear the ordinary default launcher when an APK is replaced. */
    fun keepHome(context: Context) {
        if (!isActive(context)) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        context.getSystemService(DevicePolicyManager::class.java).addPersistentPreferredActivity(
            component(context), filter, ComponentName(context, MainActivity::class.java),
        )
    }

    fun reboot(context: Context) {
        check(isActive(context)) { "Pelojack is not device owner" }
        context.getSystemService(DevicePolicyManager::class.java).reboot(component(context))
    }

    /** Gives device owner back, so Pelojack can be uninstalled (`scripts/bike-restore.sh --remove`). */
    fun release(context: Context) {
        if (!isActive(context)) return
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        policy.clearPackagePersistentPreferredActivities(component(context), context.packageName)
        policy.clearDeviceOwnerApp(context.packageName)
    }
}

/** Brings Pelojack back to the front after it updates itself; otherwise a Peloton screen takes over. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Owner.keepHome(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
