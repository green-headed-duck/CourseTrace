package com.coursetrace.app.domain

import com.coursetrace.app.model.ScheduleTimeProfile

data class ResolvedPeriodRange(
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: String,
    val endTime: String,
)

object PeriodTimeResolver {
    fun resolve(
        startPeriod: Int,
        endPeriod: Int,
        profile: ScheduleTimeProfile,
    ): ResolvedPeriodRange {
        require(startPeriod <= endPeriod) { "起始节次不能晚于结束节次" }
        val byPeriod = profile.periods.associateBy { it.period }
        val start = byPeriod[startPeriod] ?: error("作息方案中没有第 $startPeriod 节")
        val end = byPeriod[endPeriod] ?: error("作息方案中没有第 $endPeriod 节")
        require((startPeriod..endPeriod).all(byPeriod::containsKey)) { "课程节次范围不连续" }
        return ResolvedPeriodRange(startPeriod, endPeriod, start.startTime, end.endTime)
    }
}
