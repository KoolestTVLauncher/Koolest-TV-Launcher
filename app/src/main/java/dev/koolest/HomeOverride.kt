package dev.koolest

import android.accessibilityservice.AccessibilityService
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Google TV's launcher has to stay enabled (disabling it boot-loops the TV), so we take the Home key before
 * the system sees it and open our home directly; Google TV never flashes up. Holding Home still opens the
 * Google TV dashboard. Covering the TV's own launcher whenever it shows stays as the fallback for anything else
 * that opens it (e.g. Back out of the last app): Google TV's, Android TV's, or whatever this brand ships.
 */
class HomeOverride : AccessibilityService() {
    private var stock = emptySet<String>()

    override fun onServiceConnected() {
        running = this
        stock = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet() - packageName - NOT_LAUNCHERS
        serviceInfo = serviceInfo.apply { packageNames = stock.toTypedArray() } // events from those apps only
    }

    // Not a sign the user turned it off: a force-stop (TCL's background manager) unbinds it too. Koolest's own
    // switch is what records "off" (home_off); revive() respects only that.
    override fun onUnbind(intent: Intent?): Boolean {
        running = null
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Decided on release, by how long it was held: this TV's remote doesn't reliably send key repeats.
        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
            if (e.eventTime - e.downTime < LONG_PRESS_MS) goHome() else performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.packageName in stock &&
            e.className?.contains("dashboard", ignoreCase = true) != true
        ) goHome()
    }

    private fun goHome() = startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    override fun onInterrupt() {}

    companion object {
        /** The bound service, so Koolest's switch can turn it off without any permission. */
        var running: HomeOverride? = null
            private set
        /** Home apps that aren't launchers: Settings' stand-in while the TV starts up. */
        private val NOT_LAUNCHERS = setOf("com.android.settings", "com.android.tv.settings")
        private const val LONG_PRESS_MS = 500L
        private const val KEEP_JOB = 1

        private fun others(c: Context): List<String> {
            val me = ComponentName(c, HomeOverride::class.java)
            return Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotEmpty() && ComponentName.unflattenFromString(it) != me }
        }

        /** In Android's list of enabled accessibility services (compared as components: it may store the short form). */
        fun listed(c: Context): Boolean {
            val me = ComponentName(c, HomeOverride::class.java)
            return Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Android binds an accessibility service only when the enabled list changes. Once our process dies (an
         * update, a reboot, or TCL's background manager killing apps while a console is on), the service stays
         * unbound though still listed, and Home lands on Google TV. Taking it out of the list and putting it back
         * binds it again. Needs the adb-granted WRITE_SECURE_SETTINGS; leaves it off if turned off with Koolest's switch.
         */
        fun revive(c: Context, done: () -> Unit = {}) {
            val off = c.getSharedPreferences("home", MODE_PRIVATE).getBoolean("home_off", false)
            if (running != null || off ||
                c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED
            ) return done()
            val cr = c.applicationContext.contentResolver
            val me = ComponentName(c, HomeOverride::class.java).flattenToString()
            Thread {
                try {
                    val rest = others(c)
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, rest.joinToString(":"))
                    Thread.sleep(800) // two writes in a row can reach Android as one, which changes nothing
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (rest + me).joinToString(":"))
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                } finally {
                    done()
                }
            }.start()
        }

        /** A check every 15 minutes (Android's shortest), for when TCL kills us and nothing reopens the app. */
        fun keepAlive(c: Context) {
            val jobs = c.getSystemService(JobScheduler::class.java)
            if (jobs.getPendingJob(KEEP_JOB) != null) return
            jobs.schedule(JobInfo.Builder(KEEP_JOB, ComponentName(c, KeepHome::class.java))
                .setPeriodic(15 * 60_000L).setPersisted(true).build())
        }
    }
}

/** Runs [HomeOverride.revive] every 15 minutes; costs nothing while the service is running. */
class KeepHome : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        HomeOverride.revive(this) { jobFinished(p, false) }
        return true
    }

    override fun onStopJob(p: JobParameters) = false
}

/** After a reboot: bring the Home service back before anyone presses Home. */
class Booted : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pending = goAsync()
        HomeOverride.keepAlive(c)
        HomeOverride.revive(c) { pending.finish() }
    }
}
