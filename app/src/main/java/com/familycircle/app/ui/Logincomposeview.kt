package com.familycircle.app.ui

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AbstractComposeView

interface LoginActions {
    fun createFamily(name: String)
    fun joinFamily(name: String, code: String)
}

class LoginComposeView(
    context: Context,
    private val initialName: String,
    private val actions: LoginActions
) : AbstractComposeView(context) {
    private var dark by mutableStateOf(
        context.getSharedPreferences("FamilyCircle", Context.MODE_PRIVATE).getBoolean("darkMode", true)
    )

    @Composable
    override fun Content() {
        FamilyCircleTheme(darkTheme = dark) {
            LoginScreen(
                initialName = initialName,
                dark = dark,
                onToggleTheme = { toggleTheme() },
                onCreateFamily = actions::createFamily,
                onJoinFamily = actions::joinFamily
            )
        }
    }

    init { updateSystemBars() }

    private fun toggleTheme() {
        dark = !dark
        context.getSharedPreferences("FamilyCircle", Context.MODE_PRIVATE)
            .edit().putBoolean("darkMode", dark).apply()
        updateSystemBars()
    }

    private fun updateSystemBars() {
        val activity = context as? Activity ?: return
        val base = loginBase(dark).toArgb()
        activity.window.statusBarColor = base
        activity.window.navigationBarColor = base
        var flags = activity.window.decorView.systemUiVisibility
        val lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        flags = if (dark) flags and lightBars.inv() else flags or lightBars
        activity.window.decorView.systemUiVisibility = flags
    }
}
