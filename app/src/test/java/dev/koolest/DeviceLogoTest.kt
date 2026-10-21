package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLogoTest {
    private fun res(name: String) = deviceLogo(name).res

    @Test fun eachPlayStationGetsItsOwnMark() {
        assertEquals(R.drawable.logo_dev_playstation5, res("PlayStation 5"))
        assertEquals(R.drawable.logo_dev_playstation4, res("PS4"))
        assertEquals(R.drawable.logo_dev_playstation3, res("PlayStation 3"))
        assertEquals(R.drawable.logo_playstation, res("PlayStation"))
    }

    @Test fun specificNamesWinOverTheirPrefixes() {
        assertEquals(R.drawable.logo_dev_wiiu, res("Wii U"))
        assertEquals(R.drawable.logo_dev_nintendoswitch, res("NintendoSwitch"))
        assertEquals(R.drawable.logo_dev_steamdeck, res("Steam Deck"))
        assertEquals(R.drawable.logo_dev_xbox, res("Xbox Series X")) // not a set-top "box"
    }

    @Test fun discPlayersAndBrandsWithoutAColourOfTheirOwn() {
        assertEquals(R.drawable.logo_dev_disc, res("BD Player"))
        assertEquals(R.drawable.logo_dev_disc, res("Blu-ray"))
        assertEquals(0xFF000000.toInt(), deviceLogo("Apple TV").colour)
        assertEquals(0xFF009AC7.toInt(), deviceLogo("Wii U").colour)
    }

    @Test fun unknownDevicesAndBarePortsGetTheHdmiMark() {
        assertEquals(R.drawable.logo_dev_hdmi, res("HDMI 2"))
        assertEquals(R.drawable.logo_dev_hdmi, res("Gizmo"))
    }
}
