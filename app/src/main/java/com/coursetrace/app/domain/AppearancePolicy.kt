package com.coursetrace.app.domain

object AppearancePolicy {
    fun parseHexColor(value: String): Long? {
        val normalized = value.trim().removePrefix("#")
        if (normalized.length != 6 || normalized.any { it !in "0123456789abcdefABCDEF" }) return null
        return normalized.toLongOrNull(16)?.or(0xFF000000L)
    }

    fun formatHexColor(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)
}
