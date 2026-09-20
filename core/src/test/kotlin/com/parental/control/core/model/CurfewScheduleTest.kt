package com.parental.control.core.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class CurfewScheduleTest {

    @Test
    fun `daytime schedule correctly flags active hours`() {
        // Horario de 14:00 a 18:00 de Lunes a Viernes
        val schedule = CurfewSchedule(
            id = "study",
            name = "Estudio",
            daysOfWeek = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY),
            startHour = 14,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            isEnabled = true
        )

        // Lunes a las 15:30 -> DEBE estar activo
        val calInside = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(schedule.isCurfewActive(calInside))

        // Lunes a las 19:00 -> NO debe estar activo
        val calOutsideTime = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurfewActive(calOutsideTime))

        // Domingo a las 15:30 -> NO debe estar activo (no es día programado)
        val calOutsideDay = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
        }
        assertFalse(schedule.isCurfewActive(calOutsideDay))
    }

    @Test
    fun `overnight schedule crossing midnight correctly flags active hours`() {
        // Horario de Dormir de 21:00 a 07:00 (Lunes en la noche a Martes mañana)
        val schedule = CurfewSchedule(
            id = "bedtime",
            name = "Dormir",
            daysOfWeek = setOf(Calendar.MONDAY),
            startHour = 21,
            startMinute = 0,
            endHour = 7,
            endMinute = 0,
            isEnabled = true
        )

        // Lunes a las 22:30 -> DEBE estar activo
        val calMondayNight = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(schedule.isCurfewActive(calMondayNight))

        // Martes a las 05:00 (mañana siguiente al Lunes programado) -> DEBE estar activo
        val calTuesdayMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(schedule.isCurfewActive(calTuesdayMorning))

        // Martes a las 10:00 -> NO debe estar activo
        val calTuesdayDay = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurfewActive(calTuesdayDay))
    }

    @Test
    fun `disabled schedule is never active`() {
        val schedule = CurfewSchedule(
            id = "test",
            name = "Test",
            daysOfWeek = setOf(Calendar.MONDAY),
            startHour = 0,
            startMinute = 0,
            endHour = 23,
            endMinute = 59,
            isEnabled = false
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        assertFalse(schedule.isCurfewActive(cal))
    }
}
