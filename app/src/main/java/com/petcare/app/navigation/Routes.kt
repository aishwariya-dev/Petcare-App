package com.petcare.app.navigation

import android.net.Uri

/** All navigation destinations. Arguments are encoded in the route string. */
object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val HOME = "home"
    const val PETS = "pets"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"

    const val UPCOMING = "upcoming"
    const val PROFILE = "profile"
    const val ABOUT = "about"
    const val PERMISSIONS = "permissions"
    const val NEARBY_CARE = "nearby_care"

    // ── Expenses ───────────────────────────────────────────────────────────────
    const val EXPENSES = "expenses"
    const val ARG_EXPENSE_ID = "expenseId"
    const val EXPENSE_FORM = "expense_form?$ARG_EXPENSE_ID={$ARG_EXPENSE_ID}"
    fun addExpense() = "expense_form"
    fun editExpense(expenseId: Long) = "expense_form?$ARG_EXPENSE_ID=$expenseId"

    // ── Argument names ────────────────────────────────────────────────────────
    const val ARG_PET_ID = "petId"
    const val ARG_TASK_ID = "taskId"
    const val ARG_FLAGS = "flags"
    const val ARG_PHONE = "phone"
    const val ARG_TAB = "tab"
    const val ARG_START_EPOCH_DAY = "start"

    // ── Pet routes ────────────────────────────────────────────────────────────
    const val PET_DETAIL = "pet/{$ARG_PET_ID}"
    fun petDetail(petId: Long) = "pet/$petId"

    const val PET_FORM = "pet_form?$ARG_PET_ID={$ARG_PET_ID}"
    fun addPet() = "pet_form"
    fun editPet(petId: Long) = "pet_form?$ARG_PET_ID=$petId"

    // ── Task routes ───────────────────────────────────────────────────────────
    const val TASK_DETAIL = "task/{$ARG_TASK_ID}"
    fun taskDetail(taskId: Long) = "task/$taskId"

    const val TASK_FORM =
        "task_form?$ARG_TASK_ID={$ARG_TASK_ID}&$ARG_PET_ID={$ARG_PET_ID}&$ARG_START_EPOCH_DAY={$ARG_START_EPOCH_DAY}"

    /** [startEpochDay] pre-fills the date, so a task started from Upcoming lands in Upcoming. */
    fun addTask(petId: Long? = null, startEpochDay: Long? = null): String {
        val args = buildList {
            if (petId != null) add("$ARG_PET_ID=$petId")
            if (startEpochDay != null) add("$ARG_START_EPOCH_DAY=$startEpochDay")
        }
        return if (args.isEmpty()) "task_form" else "task_form?${args.joinToString("&")}"
    }
    fun editTask(taskId: Long) = "task_form?$ARG_TASK_ID=$taskId"

    const val DEFAULT_TASK_TAB = "today"
    const val TASKS_WITH_TAB = "$TASKS?$ARG_TAB={$ARG_TAB}"
    fun tasks(tab: String = DEFAULT_TASK_TAB) = "$TASKS?$ARG_TAB=$tab"

    // ── Delegation routes ──────────────────────────────────────────────────────
    const val DELEGATE = "delegate/{$ARG_PET_ID}"
    fun delegate(petId: Long) = "delegate/$petId"

    const val DELEGATE_PREVIEW = "delegate_preview/{$ARG_PET_ID}/{$ARG_FLAGS}/{$ARG_PHONE}"
    fun delegatePreview(petId: Long, flags: String, phone: String) =
        "delegate_preview/$petId/$flags/${Uri.encode(phone)}"

    /**
     * Returns the canonical identity of a top-level destination.
     *
     * The tasks destination has a tab argument, so comparing destination route
     * strings directly would make its bottom-bar selection fragile. Both
     * `tasks?tab={tab}` (the graph route) and `tasks?tab=today` (a navigation
     * request) therefore resolve to the same `tasks` identity.
     */
    fun canonicalTopLevel(route: String?): String? =
        route?.substringBefore("?")?.takeIf { it in topLevel }

    /** Visual left-to-right order used for tab transitions. */
    val topLevelOrder = listOf(HOME, PETS, TASKS, SETTINGS)

    /** Canonical destinations that show the bottom navigation bar. */
    val topLevel = topLevelOrder.toSet()
}

/** 1 moves toward a later tab, -1 toward an earlier tab, 0 has no direction. */
internal fun topLevelTabDirection(fromRoute: String?, toRoute: String?): Int {
    val from = Routes.canonicalTopLevel(fromRoute)?.let(Routes.topLevelOrder::indexOf) ?: return 0
    val to = Routes.canonicalTopLevel(toRoute)?.let(Routes.topLevelOrder::indexOf) ?: return 0
    return to.compareTo(from)
}
