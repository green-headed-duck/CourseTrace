package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.CalendarRuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HolidayCalendarPolicyTest {
    @Test
    fun bundled2026RulesMatchOfficialDates() {
        val rules = HolidayCalendarPolicy.official2026Rules

        assertEquals(39, rules.size)
        assertEquals(CalendarRuleType.WORKDAY, rules.find { it.date == "2026-09-20" }?.type)
        assertEquals(CalendarRuleType.NO_CLASS, rules.find { it.date == "2026-09-25" }?.type)
        assertEquals(CalendarRuleType.NO_CLASS, rules.find { it.date == "2026-10-07" }?.type)
        assertNotNull(rules.find { it.date == "2026-10-10" })
    }

    @Test
    fun defaultRulesAreAvailableBeforeFirstNetworkSync() {
        val state = HolidayCalendarPolicy.withBundledDefaults(AppState())

        assertEquals(39, state.calendarDayRules.size)
    }
}
