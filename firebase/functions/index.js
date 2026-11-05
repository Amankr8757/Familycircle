const { onValueCreated } = require("firebase-functions/v2/database");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp({ databaseURL: "https://familycircle-40fca-default-rtdb.asia-southeast1.firebasedatabase.app" });

const INSTANCE = "familycircle-40fca-default-rtdb";
const REGION = "asia-southeast1";

async function getTokensForUids(uids) {
  const snapshots = await Promise.all(uids.map((uid) =>
    admin.database().ref(`/pushTokens/${uid}`).get()
  ));
  return [...new Set(snapshots.map((snapshot) => snapshot.val()).filter((token) => typeof token === "string" && token.length > 20))];
}

async function sendToTokens(tokens, title, body, eventType, eventId) {
  if (!tokens.length) return;
  for (let i = 0; i < tokens.length; i += 500) {
    const result = await admin.messaging().sendEachForMulticast({
      tokens: tokens.slice(i, i + 500),
      data: { title, body, eventType, eventId: eventId || "" },
      android: { priority: "high", ttl: 60 * 60 * 1000 }
    });
    logger.info("Family push sent", { successCount: result.successCount, failureCount: result.failureCount, eventType });
  }
}

exports.notifySharingEvents = onValueCreated({
  ref: "/families/{familyId}/sharingEvents/{eventId}",
  instance: INSTANCE,
  region: REGION
}, async (event) => {
  const change = event.data.val();
  if (!change || !change.memberUid || !change.type) return;
  const familyId = event.params.familyId;
  const [family, members] = await Promise.all([
    admin.database().ref(`/families/${familyId}`).get(),
    admin.database().ref(`/families/${familyId}/members`).get()
  ]);
  if (!family.exists() || !members.exists()) return;
  const ownerUid = family.child("ownerUid").val();
  if (!ownerUid) return;
  const memberUids = Object.keys(members.val() || {});
  const uids = change.type === "paused" ? [ownerUid] : memberUids;
  const tokens = await getTokensForUids(uids);
  const name = change.memberName || "A family member";
  const type = change.type;
  let body;
  if (type === "started") body = `${name} started sharing their location.`;
  else if (type === "paused") body = `${name} paused location sharing temporarily. It is scheduled to resume automatically.`;
  else if (type === "resumed") body = `${name} resumed location sharing.`;
  else if (type === "stopped") body = `${name} stopped location sharing.`;
  else return;
  await sendToTokens(tokens, "Family Circle", body, type, event.params.eventId);
});

exports.notifySafeAreaEvents = onValueCreated({
  ref: "/families/{familyId}/safeAreaEvents/{eventId}",
  instance: INSTANCE,
  region: REGION
}, async (event) => {
  const change = event.data.val();
  if (!change || !change.memberUid || !["enter", "exit"].includes(change.type)) return;
  const familyId = event.params.familyId;
  const members = await admin.database().ref(`/families/${familyId}/members`).get();
  if (!members.exists()) return;
  const memberUids = Object.keys(members.val() || {});
  const preferences = await Promise.all(memberUids.map((uid) => admin.database().ref(`/notificationPreferences/${uid}/safeAreaAlerts`).get()));
  const optedInUids = new Set(memberUids.filter((uid, index) => preferences[index].val() === true));
  const tokenSnapshots = await Promise.all([...optedInUids].map((uid) => admin.database().ref(`/pushTokens/${uid}`).get()));
  const optedInTokens = [...new Set(tokenSnapshots.map((snapshot) => snapshot.val()).filter((token) => typeof token === "string" && token.length > 20))];
  if (!optedInTokens.length) return;
  const person = change.memberName || "A family member";
  const area = change.areaName || "the safe area";
  const arrived = change.type === "enter";
  const title = arrived ? `Arrived at ${area}` : `Left ${area}`;
  await sendToTokens(optedInTokens, title, `${person} ${arrived ? "entered" : "left"} ${area}.`, `safe_${change.type}`, event.params.eventId);
});

exports.notifyLowBatteryEvents = onValueCreated({
  ref: "/families/{familyId}/lowBatteryEvents/{eventId}",
  instance: INSTANCE,
  region: REGION
}, async (event) => {
  const change = event.data.val();
  if (!change || !change.memberUid || typeof change.battery !== "number" || change.battery > 5) return;
  const familyId = event.params.familyId;
  const members = await admin.database().ref(`/families/${familyId}/members`).get();
  if (!members.exists() || !members.child(change.memberUid).exists()) return;
  const tokens = await getTokensForUids(Object.keys(members.val() || {}));
  const person = change.memberName || "A family member";
  await sendToTokens(tokens, "Low battery", `${person}'s phone battery is at ${change.battery}%.`, "low_battery", event.params.eventId);
});
