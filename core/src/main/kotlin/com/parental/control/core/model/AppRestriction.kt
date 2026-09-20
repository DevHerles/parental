package com.parental.control.core.model

/**
 * Representa una regla de restricción para una aplicación instalada.
 */
data class AppRestriction(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = true,
    val category: AppCategory = AppCategory.SOCIAL_VIDEO,
    val dailyLimitMinutes: Int = 0, // 0 = sin límite o totalmente bloqueada
    val usedTodayMinutes: Int = 0
) {
    val isLimitExceeded: Boolean
        get() = dailyLimitMinutes > 0 && usedTodayMinutes >= dailyLimitMinutes
}

enum class AppCategory {
    SOCIAL_VIDEO,
    GAMES,
    ENTERTAINMENT,
    SYSTEM_CRITICAL,
    EDUCATIONAL,
    OTHER
}
