package com.keepsakeai.app

import com.keepsakeai.app.ui.components.simpleMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLinksTest {
    @Test fun mapsLinkIsJustGoogleMaps() {
        val md = "  * **Google Maps Link:** [View on Google Maps](https://www.google.com/maps/search/?api=1&query=AyurShop+JP+Nagar)"
        assertEquals("  •  Google Maps", simpleMarkdown(md).text)
    }

    @Test fun bareMapsUrlToo() =
        assertEquals("Directions: Google Maps", simpleMarkdown("Directions: https://maps.app.goo.gl/abc123").text)

    @Test fun otherLinksKeepTheirText() =
        assertEquals("See the course site", simpleMarkdown("See [the course site](https://example.com/course)").text)
}
