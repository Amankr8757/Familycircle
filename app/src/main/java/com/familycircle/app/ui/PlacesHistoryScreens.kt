package com.familycircle.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlacesScreen(state: FamilyUiState, actions: FamilyUiActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PLACES  ·  FAMILY SAFETY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Safe places", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Choose a personal school, college or hostel circle. Everyone can manage their own place.", color = Muted, fontSize = 14.sp)

        if (state.mySafeAreas.isEmpty()) {
            SoftCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 16.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Indigo, modifier = Modifier.size(44.dp))
                        Text("No safe area added", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text("Add school, college or hostel on the map.", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        } else state.mySafeAreas.forEach { area ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).padding(2.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.LocationOn, null, tint = safePlaceColor(area.colorIndex), modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(area.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Safe circle · ${area.radiusMeters} m", color = Muted, fontSize = 13.sp)
                    }
                    StatusChip("Active", GreenSoft, GreenOk)
                }
                OutlinedButton(onClick = { actions.removeSafeArea(area.id) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Remove ${area.name}")
                }
            }
        }

        Button(onClick = actions::chooseSafeArea, enabled = state.mySafeAreas.size < 3, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.Map, null); Spacer(Modifier.width(8.dp)); Text(if (state.mySafeAreas.size < 3) "Add safe area · ${state.mySafeAreas.size}/3" else "3 safe areas added")
        }
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Family arrival alerts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(if (state.alertsEnabled) "Enabled on this phone" else "Allow notifications to receive alerts", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = state.alertsEnabled, onCheckedChange = { actions.toggleAlerts() }, colors = SwitchDefaults.colors(checkedTrackColor = Indigo))
            }
        }
        Text("Your visible location sharing must be on for entry and exit detection. Alerts arrive on family phones where notifications are enabled.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
}

private fun safePlaceColor(index: Int) = when (Math.floorMod(index, 3)) {
    1 -> Color(0xFF159977)
    2 -> Color(0xFFEF9134)
    else -> Color(0xFF5B4FE0)
}

@Composable
fun HistoryScreen(state: FamilyUiState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FAMILY CIRCLE  ·  ACTIVITY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("Recent updates", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Safe place arrivals and departures.", color = Muted, fontSize = 14.sp)
        if (state.events.isEmpty()) {
            SoftCard {
                Icon(Icons.Outlined.History, null, tint = Indigo, modifier = Modifier.size(32.dp))
                Text("No updates yet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("When someone enters or leaves a safe area, it will show here.", color = Muted, fontSize = 13.sp)
            }
        }
        state.events.forEach { event ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(if (event.type == "enter") Icons.Outlined.Login else Icons.Outlined.Logout, null, tint = if (event.type == "enter") GreenOk else Indigo)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("${event.person} ${if (event.type == "enter") "arrived at" else "left"} ${event.area}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(event.timestamp)), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
}
