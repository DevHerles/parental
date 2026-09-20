package com.parental.control.ui.navigation

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object ChildSetup : Screen("child_setup")
    object ChildStatus : Screen("child_status")
    object ParentDashboard : Screen("parent_dashboard")
    object ParentAppManagement : Screen("parent_app_management")
    object ParentSchedules : Screen("parent_schedules")
}
