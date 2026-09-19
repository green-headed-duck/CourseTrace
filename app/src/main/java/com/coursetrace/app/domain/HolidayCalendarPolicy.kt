package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.CalendarDayRule
import com.coursetrace.app.model.CalendarRuleType
import com.coursetrace.app.model.DEFAULT_HOLIDAY_FEED_URL
import java.time.LocalDate

object HolidayCalendarPolicy {
    const val OFFICIAL_2026_PAGE = "https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"
    const val OFFICIAL_2026_UPDATED_AT = "2025-11-04"

    val official2026Rules: List<CalendarDayRule> = buildList {
        addHoliday("元旦", "2026-01-01", "2026-01-03")
        addWorkday("元旦调休工作日", "2026-01-04")
        addHoliday("春节", "2026-02-15", "2026-02-23")
        addWorkday("春节调休工作日", "2026-02-14")
        addWorkday("春节调休工作日", "2026-02-28")
        addHoliday("清明节", "2026-04-04", "2026-04-06")
        addHoliday("劳动节", "2026-05-01", "2026-05-05")
        addWorkday("劳动节调休工作日", "2026-05-09")
        addHoliday("端午节", "2026-06-19", "2026-06-21")
        addHoliday("中秋节", "2026-09-25", "2026-09-27")
        addHoliday("国庆节", "2026-10-01", "2026-10-07")
        addWorkday("国庆节调休工作日", "2026-09-20")
        addWorkday("国庆节调休工作日", "2026-10-10")
    }.sortedBy { it.date }

    fun withBundledDefaults(state: AppState): AppState {
        val profile = state.preferences.holidaySync
        if (!profile.enabled || profile.sourceUrl != DEFAULT_HOLIDAY_FEED_URL || state.calendarDayRules.isNotEmpty()) {
            return state
        }
        return state.copy(calendarDayRules = official2026Rules)
    }

    private fun MutableList<CalendarDayRule>.addHoliday(title: String, first: String, last: String) {
        var date = LocalDate.parse(first)
        val end = LocalDate.parse(last)
        while (!date.isAfter(end)) {
            add(rule(date, CalendarRuleType.NO_CLASS, title))
            date = date.plusDays(1)
        }
    }

    private fun MutableList<CalendarDayRule>.addWorkday(title: String, date: String) {
        add(rule(LocalDate.parse(date), CalendarRuleType.WORKDAY, title))
    }

    private fun rule(date: LocalDate, type: CalendarRuleType, title: String) = CalendarDayRule(
        id = "official-2026-${date}-$type",
        date = date.toString(),
        type = type,
        title = title,
        sourceUrl = DEFAULT_HOLIDAY_FEED_URL,
        sourceName = "国务院办公厅 2026 年节假日安排",
        sourcePage = OFFICIAL_2026_PAGE,
        sourceUpdatedAt = OFFICIAL_2026_UPDATED_AT,
    )
}
