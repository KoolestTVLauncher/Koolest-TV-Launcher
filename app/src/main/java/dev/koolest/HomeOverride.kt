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
    private var googleHomes = emptySet<String>()

    override fun onServiceConnected() {
        running = this
        val homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo }
        stock = homes.map { it.packageName }.toSet() - packageName - NOT_LAUNCHERS
        // An alias answers Home, but its window reports the activity behind it: keep both names.
        googleHomes = homes.filter { it.packageName == GOOGLE_TV }.flatMap { listOfNotNull(it.name, it.targetActivity) }.toSet()
        serviceInfo = serviceInfo.apply { packageNames = stock.toTypedArray() } // events from those apps only
    }

    // Not a sign the user turned it off: a force-stop (TCL's background manager) unbinds it too. Koolest's own
    // switch is what records "off" (home_off); revive() respects only that.
    override fun onUnbind(intent: Intent?): Boolean {
        running = null
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return remoteKey(e)
        // Decided on release, by how long it was held: this TV's remote doesn't reliably send key repeats.
        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
            if (e.eventTime - e.downTime < LONG_PRESS_MS) goHome() else performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
        return true
    }

    /**
     * Remote buttons set to work Everywhere (Settings → This TV → Remote buttons). Anything else goes on untouched:
     * outside Koolest only buttons someone chose are changed. Koolest actions are handed to Koolest to run.
     */
    private fun remoteKey(e: KeyEvent): Boolean {
        if (RemoteKeys.isCore(e.keyCode)) return false
        val id = RemoteKeys.id(e.keyCode, e.scanCode)
        learning?.let { learn ->
            if (RemoteKeys.fires(e.action, e.repeatCount, e.isCanceled)) learn(id)
            return true
        }
        inKoolest?.let { return it(e) } // Koolest on screen: it runs Only-in-Koolest and Everywhere buttons alike
        val prefs = getSharedPreferences("home", MODE_PRIVATE)
        val m = RemoteKeys.decode(prefs.getString("remote_keys", null)).firstOrNull { it.id == id && it.everywhere && RemoteKeys.canBeEverywhere(id, it.action) } ?: return false
        val start = when (m.action) {
            RemoteKeys.TV, RemoteKeys.MENU -> return false // the item menu only exists in Koolest
            RemoteKeys.NONE -> return true
            RemoteKeys.APP -> (packageManager.getLeanbackLaunchIntentForPackage(m.arg) ?: packageManager.getLaunchIntentForPackage(m.arg))
                ?: return false // uninstalled: the TV decides again
            RemoteKeys.ANDROID_SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            else -> Intent(this, MainActivity::class.java).putExtra(RemoteKeys.EXTRA, true)
        }
        if (RemoteKeys.fires(e.action, e.repeatCount, e.isCanceled)) {
            if (start.component?.className == MainActivity::class.java.name) RemoteKeys.pending = m
            try {
                startActivity(start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (x: Exception) {
                // nothing to open it with on this TV
            }
        }
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            covers(e.packageName?.toString(), e.className?.toString(), stock, googleHomes)
        ) goHome()
    }

    private fun goHome() = startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    override fun onInterrupt() {}

    companion object {
        /** The bound service, so Koolest's switch can turn it off without any permission. */
        var running: HomeOverride? = null
            private set
        /** Set while Koolest is on screen (onResume to onPause): it handles remote buttons itself; true = consumed. */
        var inKoolest: ((KeyEvent) -> Boolean)? = null
        /** Set by Koolest's learn page: the next non-core button goes here, from any app. */
        var learning: ((Int) -> Unit)? = null
        /** Home apps that aren't launchers: Settings' stand-in while the TV starts up. */
        private val NOT_LAUNCHERS = setOf("com.android.settings", "com.android.tv.settings")
        private const val LONG_PRESS_MS = 500L
        private const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"

        /**
         * Whether a window of the TV's own launcher should be covered by Koolest. Google TV's launcher also shows
         * sign-in, account and other screens, which a notice can open: only its home screens are covered there.
         * Other launchers' screens are unknown, so every one is covered but a dashboard (held Home).
         */
        fun covers(pkg: String?, cls: String?, stock: Set<String>, googleHomes: Set<String>) = when {
            pkg == null || pkg !in stock -> false
            cls?.contains("dashboard", ignoreCase = true) == true -> false
            pkg == GOOGLE_TV && googleHomes.isNotEmpty() -> cls in googleHomes
            else -> true
        }
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
        keepAwakeForMusic(c, false) // in case the TV restarted while music kept it awake
        HomeOverride.keepAlive(c)
        HomeOverride.revive(c) { pending.finish() }
    }
}
