package com.familycircle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapScreen(state: FamilyUiState, selectedMemberUid: String, onMapTap: (Double, Double) -> Unit, onMemberSelected: (String) -> Unit, viewAllToken: Int = 0, onViewAll: () -> Unit = {}, active: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier) {
        FamilyOsmMap(state, Modifier.fillMaxSize(), selectedMemberUid, onMapTap, active, viewAllToken)
        FilledTonalButton(
            onClick = onViewAll,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 14.dp, top = 10.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Outlined.LocationOn, null, tint = Indigo)
            Spacer(Modifier.width(7.dp))
            Text("View all · ${state.members.count { it.lat != null && it.lng != null }} locations", color = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 100.dp)
        ) {
            if (state.members.isEmpty()) {
                MemberLocationPopup(null, false, ownSharing = state.ownSharing)
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.members, key = { it.uid }) { member ->
                        MemberLocationPopup(member, member.uid == selectedMemberUid, Modifier.width(285.dp), state.ownSharing) {
                            onMemberSelected(member.uid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberLocationPopup(member: FamilyMemberUi?, selected: Boolean, modifier: Modifier = Modifier, ownSharing: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        if (member == null) {
            Text("Family map", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("Join a family to see locations", color = Muted, fontSize = 12.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(member.name, 38, Indigo, member.photoBase64)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(member.status, color = Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            Text(
                listOfNotNull(
                    member.battery?.let { "Battery $it%" },
                    member.distanceMeters?.let { "${formatDistance(it)} ${if (ownSharing && member.live) "from you" else "from last shared point"}" }
                        ?: if (member.lat != null && member.lng != null) "Distance unavailable" else null
                ).joinToString(" · ").ifBlank { "Distance unavailable" },
                color = GreenOk, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                modifier = Modifier.padding(start = 46.dp, top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (member.lat != null && member.lng != null) "${"%.5f".format(java.util.Locale.US, member.lat)}, ${"%.5f".format(java.util.Locale.US, member.lng)}" else "Location unavailable",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 46.dp, top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDistance(meters: Double): String = if (meters < 1000) {
    "${(meters / 10).toInt() * 10} m"
} else {
    "${"%.1f".format(java.util.Locale.US, meters / 1000)} km"
}
