package com.keepsakeai.app

import com.keepsakeai.app.capture.GeoTag
import com.keepsakeai.app.capture.MediaMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMetaTest {
    @Test fun parsesVideoLocation() {
        assertEquals(12.9716 to 77.5946, MediaMeta.parseIso6709("+12.9716+077.5946/"))
        assertEquals(-33.8688 to 151.2093, MediaMeta.parseIso6709("-33.8688+151.2093+012.000/"))
        assertEquals(null to null, MediaMeta.parseIso6709("+00.0000+000.0000/"))
        assertEquals(null to null, MediaMeta.parseIso6709(null))
    }

    @Test fun parsesVideoDateAndIgnoresUnset() {
        assertEquals(1794905600000L, MediaMeta.parseVideoDate("20261117T085320.000Z"))
        assertNull(MediaMeta.parseVideoDate("19040101T000000.000Z"))
    }

    @Test fun describesForTheAi() {
        val text = MediaMeta.describe(GeoTag(12.97161, 77.64123, null, "100 Feet Road, Indiranagar, Bengaluru"))
        assertEquals("Location: 12.97161, 77.64123 (near 100 Feet Road, Indiranagar, Bengaluru)", text)
    }
}
