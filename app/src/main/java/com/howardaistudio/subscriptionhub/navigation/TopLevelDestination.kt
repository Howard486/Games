package com.howardaistudio.subscriptionhub.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.howardaistudio.subscriptionhub.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object DashboardRoute

@Serializable data object SubscriptionsRoute

@Serializable data object CalendarRoute

@Serializable data object ReportRoute

/** Bottom tabs on phones, navigation rail on large screens (SA §4.3 item 3). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    DASHBOARD(DashboardRoute, DashboardRoute::class, R.string.tab_dashboard, R.drawable.ic_nav_dashboard),
    SUBSCRIPTIONS(SubscriptionsRoute, SubscriptionsRoute::class, R.string.tab_subscriptions, R.drawable.ic_nav_subscriptions),
    CALENDAR(CalendarRoute, CalendarRoute::class, R.string.tab_calendar, R.drawable.ic_nav_calendar),
    REPORT(ReportRoute, ReportRoute::class, R.string.tab_report, R.drawable.ic_nav_report),
}
