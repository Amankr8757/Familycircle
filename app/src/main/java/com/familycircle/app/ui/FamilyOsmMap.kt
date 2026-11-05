package com.familycircle.app.ui

import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.Annotation
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import java.util.concurrent.TimeUnit
import kotlin.math.log2

@Composable
fun FamilyOsmMap(
    state: FamilyUiState,
    modifier: Modifier = Modifier,
    selectedMemberUid: String = "",
    onMapTap: ((Double, Double) -> Unit)? = null,
    active: Boolean = true,
    viewAllToken: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnMapTap by rememberUpdatedState(onMapTap)
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        // Identify this app to the community tile service, as required by its usage policy.
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "FamilyCircle/0.3 (Android; OpenStreetMap map viewer)")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        MapView(context).apply { onCreate(null) }
    }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember(mapView) { mutableStateOf(false) }
    var cameraPositioned by remember(mapView) { mutableStateOf(false) }
    val lifecycleController = remember(mapView) { MapViewLifecycleController(mapView) }
    val markerImageKeys = state.members.map { listOf(it.uid, it.name, it.photoBase64) }
    val markerIcons = remember(context, markerImageKeys) {
        state.members.associate { member ->
            member.uid to IconFactory.getInstance(context).fromBitmap(createMapAvatarBitmap(member.name, member.photoBase64, 112))
        }
    }
    val markers = remember(map) { mutableMapOf<String, Marker>() }
    val safeAreaAnnotations = remember(map) { mutableListOf<Annotation>() }
    var renderedSafeAreas by remember(map) { mutableStateOf<List<SafeAreaUi>?>(null) }

    DisposableEffect(mapView, lifecycleOwner, lifecycleController) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleController.setLifecycleState(lifecycleOwner.lifecycle.currentState)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isCompassEnabled = false
            readyMap.addOnMapClickListener { point ->
                latestOnMapTap?.invoke(point.latitude, point.longitude)
                latestOnMapTap != null
            }
            readyMap.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { styleLoaded = true }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            lifecycleController.destroy()
            map = null
        }
    }
    LaunchedEffect(active, lifecycleOwner.lifecycle.currentState) {
        lifecycleController.setActive(active)
        lifecycleController.setLifecycleState(lifecycleOwner.lifecycle.currentState)
    }

    LaunchedEffect(map, styleLoaded, state.members, state.safeAreas, markerIcons) {
        val liveMap = map ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect

        val membersWithLocation = state.members.filter { it.lat != null && it.lng != null }
        val validUids = membersWithLocation.map { it.uid }.toSet()
        markers.keys.toList().filter { it !in validUids }.forEach { uid ->
            markers.remove(uid)?.let(liveMap::removeAnnotation)
        }
        val points = membersWithLocation.mapNotNull { member ->
            val lat = member.lat
            val lng = member.lng
            if (lat == null || lng == null) null else {
                val point = LatLng(lat, lng)
                val options = MarkerOptions()
                    .position(point)
                    .title(member.name)
                    .snippet(member.status + (member.battery?.let { " · $it% battery" } ?: ""))
                    .icon(markerIcons[member.uid])
                val marker = markers[member.uid]
                if (marker == null) {
                    markers[member.uid] = liveMap.addMarker(options)
                } else {
                    marker.position = point
                    marker.title = member.name
                    marker.snippet = member.status + (member.battery?.let { " · $it% battery" } ?: "")
                    marker.icon = markerIcons[member.uid]
                }
                point
            }
        }
        val safeAreasChanged = renderedSafeAreas != state.safeAreas
        if (safeAreasChanged) {
            safeAreaAnnotations.forEach(liveMap::removeAnnotation)
            safeAreaAnnotations.clear()
            renderedSafeAreas = state.safeAreas.toList()
            state.safeAreas.forEach { safe ->
                if (safe.lat != null && safe.lng != null) {
                    val safeCenter = LatLng(safe.lat, safe.lng)
                    val ring = circlePoints(safeCenter, safe.radiusMeters.toDouble())
                    val color = safeAreaColor(safe.colorIndex)
                    safeAreaAnnotations.add(liveMap.addPolygon(PolygonOptions().addAll(ring).fillColor((0x38 shl 24) or (color and 0x00FFFFFF)).strokeColor(color)))
                    safeAreaAnnotations.add(liveMap.addPolyline(PolylineOptions().addAll(ring + ring.first()).color(color).width(3f)))
                }
            }
        }
        val areaCenters = state.safeAreas.mapNotNull { safe ->
            if (safe.lat == null || safe.lng == null) null else {
            val safeCenter = LatLng(safe.lat, safe.lng)
            safeCenter
            }
        }

        if (!cameraPositioned && (points.isNotEmpty() || areaCenters.isNotEmpty())) {
            val targets = (points + areaCenters).distinct()
            val center = LatLng(targets.map { it.latitude }.average(), targets.map { it.longitude }.average())
            val zoom = if (targets.size == 1) 14.0 else 11.0
            liveMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
            cameraPositioned = true
        }
    }

    LaunchedEffect(map, styleLoaded, viewAllToken, state.members) {
        val liveMap = map ?: return@LaunchedEffect
        if (!styleLoaded || viewAllToken == 0) return@LaunchedEffect
        val points = state.members.mapNotNull { member ->
            val lat = member.lat
            val lng = member.lng
            if (lat == null || lng == null) null else LatLng(lat, lng)
        }.distinct()
        if (points.isEmpty()) return@LaunchedEffect
        val samePoint = points.distinctBy { "${it.latitude},${it.longitude}" }.size == 1
        if (points.size == 1 || samePoint) {
            liveMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 11.0))
        } else {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(it) }
            val bounds = builder.build()
            val density = context.resources.displayMetrics.density
            liveMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                bounds,
                (24 * density).toInt(), (112 * density).toInt(),
                (24 * density).toInt(), (300 * density).toInt()
            ))
        }
    }

    LaunchedEffect(map, styleLoaded, selectedMemberUid, state.members) {
        val liveMap = map ?: return@LaunchedEffect
        if (!styleLoaded || selectedMemberUid.isBlank()) return@LaunchedEffect
        state.members.firstOrNull { it.uid == selectedMemberUid && it.lat != null && it.lng != null }?.let { member ->
            liveMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(member.lat!!, member.lng!!), 15.0))
        }
    }

    Box(modifier) {
        AndroidView(
            factory = {
                (mapView.parent as? ViewGroup)?.removeView(mapView)
                mapView
            },
            update = { it.visibility = if (active) View.VISIBLE else View.INVISIBLE },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "© OpenStreetMap contributors",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .shadow(2.dp, RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

private fun safeAreaColor(index: Int): Int = when (Math.floorMod(index, 3)) {
    1 -> AndroidColor.rgb(21, 153, 119)
    2 -> AndroidColor.rgb(239, 145, 52)
    else -> AndroidColor.rgb(91, 79, 224)
}

private const val OSM_RASTER_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm-raster", "type": "raster", "source": "osm" }]
}
"""

private fun zoomForRadius(radiusMeters: Double): Double =
    (16.0 - log2((radiusMeters / 100.0).coerceAtLeast(1.0))).coerceIn(11.0, 17.0)

private fun circlePoints(center: LatLng, radiusMeters: Double): List<LatLng> {
    val earthRadius = 6_371_000.0
    val angularDistance = radiusMeters / earthRadius
    val latitude1 = Math.toRadians(center.latitude)
    val longitude1 = Math.toRadians(center.longitude)
    return (0..360 step 6).map { bearingDegrees ->
        val bearing = Math.toRadians(bearingDegrees.toDouble())
        val latitude2 = kotlin.math.asin(
            kotlin.math.sin(latitude1) * kotlin.math.cos(angularDistance) +
                kotlin.math.cos(latitude1) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing)
        )
        val longitude2 = longitude1 + kotlin.math.atan2(
            kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(latitude1),
            kotlin.math.cos(angularDistance) - kotlin.math.sin(latitude1) * kotlin.math.sin(latitude2)
        )
        LatLng(Math.toDegrees(latitude2), Math.toDegrees(longitude2))
    }
}

private class MapViewLifecycleController(private val mapView: MapView) {
    private var active = true
    private var lifecycleState = Lifecycle.State.INITIALIZED
    private var started = false
    private var resumed = false
    private var destroyed = false

    fun setActive(value: Boolean) {
        active = value
        sync()
    }

    fun setLifecycleState(value: Lifecycle.State) {
        lifecycleState = value
        sync()
    }

    private fun sync() {
        if (destroyed) return
        val shouldStart = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        if (shouldStart && !started) {
            mapView.onStart()
            started = true
        } else if (!shouldStart && started) {
            if (resumed) { mapView.onPause(); resumed = false }
            mapView.onStop()
            started = false
            return
        }

        val shouldResume = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
        if (shouldResume && started && !resumed) {
            mapView.onResume()
            resumed = true
        } else if (!shouldResume && resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    fun destroy() {
        if (destroyed) return
        active = false
        lifecycleState = Lifecycle.State.CREATED
        sync()
        if (started) { mapView.onStop(); started = false }
        mapView.onDestroy()
        destroyed = true
    }
}
