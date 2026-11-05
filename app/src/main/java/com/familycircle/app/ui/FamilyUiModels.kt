package com.familycircle.app.ui

import android.content.Context
import android.app.Activity
import android.graphics.Color
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView

data class FamilyMemberUi(
    val uid: String,
    val name: String,
    val status: String,
    val live: Boolean,
    val battery: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val photoBase64: String? = null,
    val distanceMeters: Double? = null,
    val locationUpdatedAt: Long? = null
)

data class FamilyEventUi(val id: String, val person: String, val area: String, val type: String, val timestamp: Long)
data class SafeAreaUi(val name: String, val radiusMeters: Int, val lat: Double? = null, val lng: Double? = null, val ownerUid: String = "", val ownerName: String = "", val id: String = "legacy", val colorIndex: Int = 0)
data class JoinRequestUi(val uid: String, val name: String)

data class FamilyUiState(
    val userName: String,
    val myPhotoBase64: String?,
    val familyCode: String,
    val owner: Boolean,
    val ownSharing: Boolean,
    val pauseUntil: Long,
    val sharingCount: Int,
    val members: List<FamilyMemberUi>,
    val events: List<FamilyEventUi>,
    val safeArea: SafeAreaUi?,
    val safeAreas: List<SafeAreaUi>,
    val mySafeAreas: List<SafeAreaUi>,
    val alertsEnabled: Boolean,
    val requests: List<JoinRequestUi>
)

interface FamilyUiActions {
    fun openMap()
    fun openHistory()
    fun showMemberLocation(uid: String)
    fun uploadProfilePhoto()
    fun changeName()
    fun mapTapped(lat: Double, lng: Double)
    fun toggleSharing()
    fun chooseSafeArea()
    fun removeSafeArea(id: String)
    fun toggleAlerts()
    fun shareFamilyCode()
    fun leaveFamily()
    fun approve(uid: String, name: String)
    fun checkUpdates()
}

class FamilyCircleComposeView(
    context: Context,
    initialState: FamilyUiState,
    private val actions: FamilyUiActions
) :  AbstractComposeView(context) {
    private var state by mutableStateOf(initialState)
    private var selectedTab by mutableStateOf(Tab.Home)
    private var mapWasOpened by mutableStateOf(false)
    private var selectedMemberUid by mutableStateOf("")
    private var viewAllToken by mutableStateOf(0)
    private var darkMode by mutableStateOf(
        context.getSharedPreferences("FamilyCircle", Context.MODE_PRIVATE)
            .getBoolean("darkMode", true)
    )

    @Composable
    override fun Content() {
        FamilyCircleTheme(darkTheme = darkMode) {
            FamilyCircleApp(
                state = state,
                selectedTab = selectedTab,
                selectedMemberUid = selectedMemberUid,
                viewAllToken = viewAllToken,
                darkMode = darkMode,
                actions = actions,
                keepMapAlive = mapWasOpened,
                onViewAll = { selectedMemberUid = ""; viewAllToken++ },
                onTabSelected = { tab -> if (tab == Tab.Map) mapWasOpened = true; selectedTab = tab },
                onDarkModeChange = { enabled ->
                    darkMode = enabled
                    context.getSharedPreferences("FamilyCircle", Context.MODE_PRIVATE)
                        .edit().putBoolean("darkMode", enabled).apply()
                    updateSystemBars(enabled)
                }
            )
        }
    }

    init { updateSystemBars(darkMode) }

    private fun updateSystemBars(dark: Boolean) {
        val activity = context as? Activity ?: return
        activity.window.statusBarColor = if (dark) Color.rgb(22, 25, 33) else Color.rgb(246, 246, 252)
        activity.window.navigationBarColor = if (dark) Color.rgb(22, 25, 33) else Color.WHITE
        var flags = activity.window.decorView.systemUiVisibility
        flags = if (dark) {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        } else {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        activity.window.decorView.systemUiVisibility = flags
    }

    fun update(newState: FamilyUiState) { state = newState }
    fun navigateTo(tab: Tab) { if (tab == Tab.Map) mapWasOpened = true; selectedTab = tab }
    fun getCurrentTab(): Tab = selectedTab
    fun openMapForPicking() { mapWasOpened = true; selectedTab = Tab.Map }
    fun showMemberOnMap(uid: String) { mapWasOpened = true; selectedMemberUid = uid; selectedTab = Tab.Map }
    fun viewAllMembers() { mapWasOpened = true; selectedTab = Tab.Map; viewAllToken++ }
}
