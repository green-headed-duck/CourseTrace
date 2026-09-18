package com.coursetrace.app.domain

import com.coursetrace.app.model.AppState
import com.coursetrace.app.model.Term
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Personal academic-calendar anchor confirmed by the user for 2026-2027-1. */
object AcademicTermPolicy {
    val confirmedStartDate: LocalDate = LocalDate.of(2026, 8, 31)
    const val confirmedName: String = "2026-2027学年第1学期"
    const val confirmedWeekCount: Int = 22

    private val confirmedEndDate: LocalDate = confirmedStartDate.plusWeeks(confirmedWeekCount.toLong()).minusDays(1)

    fun defaultTerm(today: LocalDate = LocalDate.now()): Term {
        val inConfirmedTerm = today in confirmedStartDate..confirmedEndDate
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return if (inConfirmedTerm) {
            Term(name = confirmedName, startDate = confirmedStartDate.toString(), weekCount = confirmedWeekCount)
        } else {
            Term(name = "当前学期", startDate = monday.toString())
        }
    }

    fun startDateForCurrentWeek(today: LocalDate, currentWeek: Int): LocalDate {
        require(currentWeek in 1..40) { "当前教学周应在 1 到 40 之间" }
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.minusWeeks((currentWeek - 1).toLong())
    }

    fun weekNumber(startDate: String, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(LocalDate.parse(startDate), date).floorDiv(7) + 1).toInt()

    /** Repairs the old 0.2.6 default that incorrectly made an install week the first teaching week. */
    fun repairKnown2026Term(state: AppState): AppState {
        val activeId = state.activeTermId ?: return state
        val active = state.terms.find { it.id == activeId } ?: return state
        val oldStart = runCatching { LocalDate.parse(active.startDate) }.getOrNull() ?: return state
        val wasCreatedInsideConfirmedTerm = oldStart > confirmedStartDate && oldStart <= confirmedEndDate
        if (!wasCreatedInsideConfirmedTerm) return state

        return state.copy(
            terms = state.terms.map { term ->
                if (term.id != activeId) term else term.copy(
                    name = if (term.name == "当前学期") confirmedName else term.name,
                    startDate = confirmedStartDate.toString(),
                    weekCount = maxOf(term.weekCount, confirmedWeekCount),
                )
            },
        )
    }
}
