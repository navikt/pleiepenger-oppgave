package no.nav.helse.oppgave.v1

import java.time.LocalDate
import java.time.DayOfWeek.*
import java.time.ZoneOffset

/**
 * Kopi fra https://github.com/navikt/helse-maksdato/blob/master/src/main/kotlin/no/nav/helse/CalendarArithmetic.kt
 * @ f47424edcdf143130a98d71a8877f4ad147a9bf1
 */
object DateUtils {

    fun nWeekdaysFromToday(n: Int) : LocalDate {
        return nWeekdaysFrom(n = n, from = LocalDate.now(ZoneOffset.UTC))
    }

    tailrec fun nWeekdaysFrom(n: Int, from: LocalDate): LocalDate =
        if (n == 0) from else nWeekdaysFrom(n - 1, nextWeekday(from))

    tailrec fun nextWeekday(after: LocalDate): LocalDate {
        val nextDay = after.plusDays(1)
        return if (isWeekend(nextDay)) nextWeekday(nextDay) else nextDay
    }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == SATURDAY || date.dayOfWeek == SUNDAY
}