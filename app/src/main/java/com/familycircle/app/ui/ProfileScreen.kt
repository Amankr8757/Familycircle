package com.familycircle.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(state: FamilyUiState, darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit, actions: FamilyUiActions) {
    var familyExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(IndigoLight, Indigo)),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialsAvatar(state.userName, 64, photoBase64 = state.myPhotoBase64)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(state.userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Family member · sharing is optional", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(Modifier.weight(1f), "${state.members.size}", "Members")
                StatCard(Modifier.weight(1f), if (state.ownSharing) "On" else "Off", "Sharing")
                StatCard(Modifier.weight(1f), if (state.owner) "Owner" else "Member", "Role")
            }

            OutlinedButton(onClick = actions::checkUpdates, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.SystemUpdateAlt, null); Spacer(Modifier.width(8.dp)); Text("Check for app updates")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = actions::changeName, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit name", fontSize = 13.sp)
                }
                OutlinedButton(onClick = actions::uploadProfilePhoto, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, null); Spacer(Modifier.width(6.dp))
                    Text(if (state.myPhotoBase64 == null) "Add photo" else "Change photo", fontSize = 13.sp)
                }
            }

            SectionTitle("My Circle")
            SoftCard {
                Row(
                    Modifier.fillMaxWidth().clickable { familyExpanded = !familyExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = Indigo)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Family Circle", fontWeight = FontWeight.Medium)
                        Text("${state.members.size} members · ${if (familyExpanded) "Tap to hide" else "Tap to view"}", color = Muted, fontSize = 13.sp)
                    }
                    Icon(if (familyExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = Muted)
                }
                if (familyExpanded) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(12.dp))
                    if (state.members.isEmpty()) {
                        Text("No family members yet", color = Muted, fontSize = 13.sp)
                    } else {
                        state.members.forEach { member ->
                            MemberCard(member, onClick = { actions.showMemberLocation(member.uid) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            SoftCard {
                Text("Family code", fontSize = 13.sp, color = Muted)
                Text(state.familyCode, fontSize = 18.sp, color = Indigo, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Button(onClick = actions::shareFamilyCode, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share family code")
                }
            }

            if (state.owner) {
                SectionTitle("Join requests · owner approval")
                if (state.requests.isEmpty()) SoftCard { Text("No pending requests", color = Muted, fontSize = 14.sp) }
                state.requests.forEach { request ->
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${request.name} wants to join", Modifier.weight(1f), fontSize = 14.sp)
                            TextButton(onClick = { actions.approve(request.uid, request.name) }) { Text("Approve") }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = actions::leaveFamily, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Leave this family on this phone") }
            }

            SectionTitle("Location sharing")
            SoftCard {
                ToggleRow(Icons.Outlined.MyLocation, "Share my location", "Visible while sharing is on", state.ownSharing) { actions.toggleSharing() }
            }
            SectionTitle("Appearance")
            SoftCard {
                ToggleRow(Icons.Outlined.DarkMode, "Dark mode", "Switch between light and dark theme", darkMode, onDarkModeChange)
            }
            SectionTitle("Privacy")
            Text("Only approved family members can view shared locations. The app shares your display name, location while sharing, battery level and update time. It does not read messages, calls or contacts.", color = Muted, fontSize = 13.sp)
            Text("Maps use OpenStreetMap data and need internet access. © OpenStreetMap contributors.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.navigationBarsPadding().height(110.dp))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String) {
    SoftCard(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Indigo)
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))

@Composable
fun MemberCard(member: FamilyMemberUi, onClick: (() -> Unit)? = null) {
    SoftCard(modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(member.name, 44, Indigo, member.photoBase64)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(member.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(member.status + (member.battery?.let { " · $it% battery" } ?: ""), fontSize = 12.sp, color = Muted)
            }
            StatusChip(if (member.live) "Live" else "Offline", if (member.live) GreenSoft else IndigoSoft, if (member.live) GreenOk else Muted)
        }
        if (member.lat != null && member.lng != null) {
            Text("${"%.5f".format(java.util.Locale.US, member.lat)}, ${"%.5f".format(java.util.Locale.US, member.lng)}", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 56.dp, top = 4.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Indigo)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Indigo))
    }
}
