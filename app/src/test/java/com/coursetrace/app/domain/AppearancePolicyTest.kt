package com.coursetrace.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearancePolicyTest {
    @Test
    fun parsesRgbHexAsOpaqueArgb() {
        assertEquals(0xFF4F46E5L, AppearancePolicy.parseHexColor("#4f46e5"))
        assertEquals("#4F46E5", AppearancePolicy.formatHexColor(0xFF4F46E5L))
    }

    @Test
    fun rejectsUnsupportedColorText() {
        assertNull(AppearancePolicy.parseHexColor("#FFF"))
        assertNull(AppearancePolicy.parseHexColor("purple"))
        assertNull(AppearancePolicy.parseHexColor("#12345G"))
    }
}
