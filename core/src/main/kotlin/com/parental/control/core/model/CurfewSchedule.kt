package com.parental.control.core.model

import java.util.Calendar

/**
 * Representa un horario de restricción o toque de queda (ej. Horas de estudio o dormir).
 */
data class CurfewSchedule(
    val id: String,
    val name: String,
    val daysOfWeek: Set<Int> = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY
    ),
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isEnabled: Boolean = true
) {
    /**
     * Evalúa si un momento determinado cae dentro de este horario restringido.
     * Soporta rangos dentro del mismo día (ej. 14:00 - 18:00)
     * y rangos que cruzan la medianoche (ej. 21:00 - 07:00).
     */
    fun isCurfewActive(calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled) return false

        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutesOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            // Rango dentro del mismo día (ej. 14:00 a 18:00)
            currentDay in daysOfWeek && currentMinutesOfDay in startMinutes..endMinutes
        } else {
            // Rango que cruza la medianoche (ej. 21:00 a 07:00)
            if (currentMinutesOfDay >= startMinutes) {
                // Parte de la noche (21:00 a 23:59) en los días programados
                currentDay in daysOfWeek
            } else if (currentMinutesOfDay <= endMinutes) {
                // Parte de la mañana (00:00 a 07:00) - Corresponde al día siguiente del programado
                val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
                previousDay in daysOfWeek
            } else {
                false
            }
        }
    }
}
