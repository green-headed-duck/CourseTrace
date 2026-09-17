package com.coursetrace.app.domain

import com.coursetrace.app.model.ScheduleTimeProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodTimeResolverTest {
    @Test
    fun spansBreaksUsingFirstStartAndLastEnd() {
        val range = PeriodTimeResolver.resolve(7, 8, ScheduleTimeProfile())

        assertEquals("15:45", range.startTime)
        assertEquals("17:20", range.endTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedPeriods() {
        PeriodTimeResolver.resolve(4, 3, ScheduleTimeProfile())
    }
}
