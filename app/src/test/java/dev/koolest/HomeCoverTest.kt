package dev.koolest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoverTest {
    private val gtv = "com.google.android.apps.tv.launcherx"
    private val stock = setOf(gtv, "com.tcl.home")
    private val others = setOf("$gtv.profile.chooser.ProfileChooserActivity", "$gtv.account.SignInActivity")

    @Test fun googleTvHomeScreensAreCovered() {
        assertTrue(HomeOverride.covers(gtv, "$gtv.home.HomeActivity", stock, others))
        assertTrue(HomeOverride.covers(gtv, "$gtv.home.VanillaModeHomeActivity", stock, others))
    }

    @Test fun googleTvOtherScreensAreLeftAlone() {
        assertFalse(HomeOverride.covers(gtv, "$gtv.account.SignInActivity", stock, others))
        assertFalse(HomeOverride.covers(gtv, "$gtv.profile.chooser.ProfileChooserActivity", stock, others))
        assertFalse(HomeOverride.covers(gtv, "$gtv.dashboard.DashboardActivity", stock, others))
    }

    @Test fun googleTvUnknownNamesAreStillCovered() {
        assertTrue(HomeOverride.covers(gtv, "android.widget.FrameLayout", stock, others))
        assertTrue(HomeOverride.covers(gtv, "$gtv.home.SomeNewHomeActivity", stock, others))
        assertTrue(HomeOverride.covers(gtv, "$gtv.account.SignInActivity", stock, emptySet()))
    }

    @Test fun otherLaunchersAreCoveredExceptTheirDashboard() {
        assertTrue(HomeOverride.covers("com.tcl.home", "com.tcl.home.Anything", stock, others))
        assertFalse(HomeOverride.covers("com.tcl.home", "com.tcl.home.DashboardPanel", stock, others))
        assertFalse(HomeOverride.covers("com.netflix.ninja", "x", stock, others))
        assertFalse(HomeOverride.covers(null, null, stock, others))
    }
}
