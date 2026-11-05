# Family Circle for Android

A consent-based family location-sharing starter built for Android Studio. Each person installs the app and chooses when to share. The family owner approves every join request. While sharing, Android displays an ongoing notification with a Stop action.

## Included

- Android app written in Java (min Android 8 / API 26)
- Firebase anonymous sign-in and Realtime Database
- Random family code and owner approval for new members
- Location collection starts only after the person taps **Start sharing** and grants Android location permission
- A foreground location service updates the current location about every 30 seconds while active
- Family list shows live or stale status, battery percentage, last update, and coordinates
- Each member can upload a small profile picture; approved family members see it in member cards and map markers
- Map cards show approximate straight-line distance from this phone when this phone is sharing a recent location
- Built-in OpenStreetMap raster map with family member markers and a safe-area circle (rendered by MapLibre Native)
- Home, Map, Places, History, and Profile tabs adapted from the supplied UI screens, with live family data and the real built-in map
- Android launcher icon based on the supplied family navigation pin artwork
- Each family member can set a personal safe-area circle on the map; entry/exit events can notify family phones that enable alerts
- **Stop sharing** removes that member's current location from the database
- Firebase rules restrict family reads to members and each location write to its owner

The app does not collect messages, calls, contacts, IMEI, or other device identifiers. There is no hidden tracking.

## Connect your Firebase project

1. Open the `family-circle-android` folder in Android Studio.
2. Create a Firebase project in the [Firebase console](https://console.firebase.google.com/). Add an Android app with package name `com.familycircle.app`.
3. Create its **Realtime Database**. Copy the database URL shown by Firebase and replace the placeholder in `app/src/main/res/values/strings.xml`. Download `google-services.json` and put it in this project's `app/` folder (next to `app/build.gradle`). Keep that config file private; `.gitignore` excludes it.
4. In Firebase Authentication, enable **Anonymous** sign-in.
5. In Realtime Database → Rules, paste the rules from `firebase/database.rules.json` and publish them. These rules include personal safe areas and family-only profile photo data. Do not use public read/write rules.
6. In Android Studio, sync Gradle. Then run the app on two physical phones. Both phones need internet, location services, and the same app build.

The provided Firebase config matches the Android package name. The database URL is set in `app/src/main/res/values/strings.xml` for the configured family project.

## OpenStreetMap map

The app renders the standard OpenStreetMap raster tiles with MapLibre Native. No Google Maps API key is needed. The map needs internet access and displays the required “© OpenStreetMap contributors” attribution. It requests only tiles for the visible map and does not offer offline downloads or bulk prefetching. The linked OSM API v0.6 is the editing/data API; it is not the rendered tile service used to draw the map.

## First use

1. On the family owner's phone, enter a display name and tap **Create a family**.
2. Tap **Share family code** and send that code privately to family members.
3. On another phone, enter a display name and the code, then tap **Request to join**.
4. The owner approves the request on the family screen.
5. Each person turns on sharing only when they want to. They can stop in the app or from the visible notification.

## Safe area alerts

1. Any approved family member can open **Places** → **Choose my safe area**, then tap their school, college or hostel center on the map.
2. Enter an area name and a radius from 100 to 3,000 meters. The default is 300 meters.
3. Enable **Family arrival alerts** and allow notifications on each phone that should receive alerts.
4. Keep location sharing on for the person whose safe area should be monitored. Entry/exit checks run alongside that person's visible location-sharing service.

Each personal area is stored separately under its owner's member ID. Family phones with alerts enabled receive local notifications while the app is connected; Android stopping the listener or losing its Firebase connection can delay alerts until it reconnects. The safe-area settings and event paths are protected by `firebase/database.rules.json`; publish those updated rules in Firebase Console before using the feature.

The family code is a long random value. Only share it with people you trust. Anonymous accounts are stored on each device; after reinstalling or clearing app data, that phone needs a new owner approval.

## Location and privacy behavior

- The location service starts from the visible app after permission is granted. It declares the Android `location` foreground-service type and keeps an ongoing notification while active.
- Stopping sharing deletes the latest location. The app only shows coordinates received in the last hour and labels updates older than two minutes as stale.
- If Android or the user force-stops the app, the last sample may remain in Firebase and is shown as stale; reopen the app and tap Stop sharing to clear it.
- The service does not request `ACCESS_BACKGROUND_LOCATION`; it is started by the user while the app is visible. Android requires location permissions before starting a location foreground service.
- Safe-area monitoring uses the same user-started foreground location service; it stops when the child turns off sharing. The owner must enable notification permission to receive entry/exit alerts.
- The built-in map uses MapLibre Native with OpenStreetMap raster tiles. It needs internet but no map API key. Family coordinates are read from Firebase and shown as native map markers.
- Anonymous Firebase sign-in is convenient for a family prototype, but reinstalling changes a phone's identity. Before public release, consider account recovery, a privacy policy, abuse controls, and review of Android and Google Play location requirements.

## Build

Use Android Studio with Android SDK 35. Gradle sync downloads Firebase and Google Play Services libraries. The app needs your own `google-services.json` and published database rules before it can connect to Firebase.
