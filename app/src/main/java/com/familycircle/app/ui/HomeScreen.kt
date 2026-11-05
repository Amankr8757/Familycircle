package com.familycircle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: FamilyUiState,
    onOpenMap: () -> Unit,
    onOpenHistory: () -> Unit,
    onToggleSharing: () -> Unit,
    onAddSafeArea: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialsAvatar(state.userName, size = 46, photoBase64 = state.myPhotoBase64)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Welcome, ${state.userName}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()), color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = "Recent alerts", tint = Indigo)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Groups, null, tint = Indigo)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Your family", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("${state.sharingCount} of ${state.members.size} sharing", color = Muted, fontSize = 13.sp)
                    }
                    StatusChip(
                        if (state.ownSharing) "Sharing" else if (state.pauseUntil > System.currentTimeMillis()) "Paused" else "Private",
                        if (state.ownSharing) GreenSoft else IndigoSoft,
                        if (state.ownSharing) GreenOk else Indigo
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Safe area", color = Muted, fontSize = 12.sp)
                        Text(state.safeArea?.name ?: "Not set yet", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                    TextButton(onClick = if (state.safeArea == null) onAddSafeArea else onOpenMap) {
                        Text(state.safeArea?.let { "${it.radiusMeters} m" } ?: "Add place")
                    }
                }
                Button(
                    onClick = onToggleSharing,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(if (state.ownSharing) Icons.Outlined.LocationOff else Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text(when {
                        state.ownSharing -> "Pause sharing"
                        state.pauseUntil > System.currentTimeMillis() -> "Resume sharing now"
                        else -> "Start sharing"
                    })
                }
                if (state.pauseUntil > System.currentTimeMillis()) {
                    Text(
                        "Sharing resumes at ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(state.pauseUntil))}.",
                        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Live family map", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                TextButton(onClick = onOpenMap) { Text("Open map") }
            }
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                FamilyOsmMap(state, modifier = Modifier.fillMaxWidth().height(210.dp))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Family members", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("${state.members.size}", color = Muted, fontSize = 13.sp)
            }
            if (state.members.isEmpty()) {
                SoftCard { Text("No family members yet", color = Muted, fontSize = 14.sp) }
            } else {
                state.members.forEach { member -> MemberCard(member, onClick = { onMemberClick(member.uid) }) }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent updates", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                TextButton(onClick = onOpenHistory) { Text("See all") }
            }
            SoftCard {
                val event = state.events.firstOrNull()
                if (event == null) {
                    Text("No place alerts yet", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("School or hostel arrival alerts will appear here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(
                        "${event.person} ${if (event.type == "enter") "arrived at" else "left"} ${event.area}",
                        fontWeight = FontWeight.Medium, fontSize = 14.sp
                    )
                    Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.timestamp)), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(110.dp))
        }
    }
}