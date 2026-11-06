package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoverTest {
    private val gtv = "com.google.android.apps.tv.launcherx"
    private val stock = setOf(gtv, "com.tcl.home")
    private val homes = setOf("$gtv.home.HomeActivity", "$gtv.home.VanillaModeHomeActivity")

    @Test fun googleTvHomeScreensAreCovered() {
        assertTrue(HomeOverride.covers(gtv, "$gtv.home.HomeActivity", stock, homes))
        assertTrue(HomeOverride.covers(gtv, "$gtv.home.VanillaModeHomeActivity", stock, homes))
    }

    @Test fun googleTvOtherScreensAreLeftAlone() {
        assertFalse(HomeOverride.covers(gtv, "$gtv.account.SignInActivity", stock, homes))
        assertFalse(HomeOverride.covers(gtv, "android.widget.FrameLayout", stock, homes))
        assertFalse(HomeOverride.covers(gtv, "$gtv.dashboard.DashboardActivity", stock, homes))
    }

    @Test fun googleTvWithoutAKnownHomeKeepsTheOldRule() {
        assertTrue(HomeOverride.covers(gtv, "$gtv.account.SignInActivity", stock, emptySet()))
    }

    @Test fun otherLaunchersAreCoveredExceptTheirDashboard() {
        assertTrue(HomeOverride.covers("com.tcl.home", "com.tcl.home.Anything", stock, homes))
        assertFalse(HomeOverride.covers("com.tcl.home", "com.tcl.home.DashboardPanel", stock, homes))
        assertFalse(HomeOverride.covers("com.netflix.ninja", "x", stock, homes))
        assertFalse(HomeOverride.covers(null, null, stock, homes))
    }
}
