package com.petcare.app.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.petcare.app.reminder.ReminderNotification
import com.petcare.app.reminder.hasSmsTelephonyFeature

/** SMS state as the permissions page and the task form show it. */
enum class SmsPermission {
    GRANTED,
    NOT_GRANTED,
    /** A request just came back denied without a rationale: Android's restricted-settings block. */
    BLOCKED,
    /** The device has no SMS telephony; SMS rows are hidden. */
    UNSUPPORTED
}

/**
 * The one place that reads device permission state. Screens ask this facade
 * and never touch the framework checks directly, so the four permissions the
 * app depends on are judged the same way everywhere.
 */
interface DevicePermissions {
    fun notificationsAllowed(): Boolean
    /** True when a system prompt is the right next step (Android 13+, not yet granted). */
    fun canRequestNotifications(): Boolean
    /** Only Android 12+ gates exact alarms; earlier versions are always allowed. */
    fun exactAlarmsAllowed(): Boolean
    fun exactAlarmsConfigurable(): Boolean
    fun sms(activity: Activity?): SmsPermission
    /** Remembers that the last SEND_SMS request was denied, so [sms] can tell Blocked apart. */
    fun recordSmsDenied()
    fun batteryUnrestricted(): Boolean

    fun openNotificationSettings(context: Context)
    fun openExactAlarmSettings(context: Context)
    fun openAppSettings(context: Context)
    fun requestBatteryUnrestricted(context: Context)
}

class AndroidDevicePermissions(context: Context) : DevicePermissions {
    private val appContext = context.applicationContext

    @Volatile
    private var smsDeniedOnce = false

    override fun notificationsAllowed(): Boolean = ReminderNotification.canPost(appContext)

    override fun canRequestNotifications(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderNotification.hasRuntimePermission(appContext)

    override fun exactAlarmsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    override fun exactAlarmsConfigurable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun sms(activity: Activity?): SmsPermission {
        if (!hasSmsTelephonyFeature(appContext.packageManager, Build.VERSION.SDK_INT)) {
            return SmsPermission.UNSUPPORTED
        }
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            smsDeniedOnce = false
            return SmsPermission.GRANTED
        }
        val blocked = smsDeniedOnce && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)
        return if (blocked) SmsPermission.BLOCKED else SmsPermission.NOT_GRANTED
    }

    override fun recordSmsDenied() {
        smsDeniedOnce = true
    }

    override fun batteryUnrestricted(): Boolean =
        appContext.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(appContext.packageName) == true

    override fun openNotificationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        })
    }

    override fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
    }

    override fun openAppSettings(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    override fun requestBatteryUnrestricted(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        if (!start(context, direct)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun start(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
