package com.familycircle.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FamilyCircleApp(
    state: FamilyUiState,
    selectedTab: Tab,
    selectedMemberUid: String,
    viewAllToken: Int,
    darkMode: Boolean,
    keepMapAlive: Boolean,
    actions: FamilyUiActions,
    onTabSelected: (Tab) -> Unit,
    onViewAll: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit
) {
    // Surface gives all Text the right default colour (white in dark mode).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(Modifier.fillMaxSize()) {
            if (keepMapAlive || selectedTab == Tab.Map) {
                MapScreen(
                    state, selectedMemberUid, { lat, lng -> actions.mapTapped(lat, lng) },
                    actions::showMemberLocation,
                    viewAllToken = viewAllToken,
                    onViewAll = onViewAll,
                    active = selectedTab == Tab.Map,
                    modifier = if (selectedTab == Tab.Map) Modifier.fillMaxSize() else Modifier.size(0.dp)
                )
            }
            when (selectedTab) {
                Tab.Home -> HomeScreen(state, actions::openMap, actions::openHistory, actions::toggleSharing, actions::chooseSafeArea, actions::showMemberLocation)
                Tab.Map -> Unit
                Tab.Places -> PlacesScreen(state, actions)
                Tab.History -> HistoryScreen(state)
                Tab.Profile -> ProfileScreen(state, darkMode, onDarkModeChange, actions)
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                BottomNavBar(selectedTab, onTabSelected)
            }
        }
    }
}
