package com.altinarama.harita

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Harita katmanlarını (altın noktaları ve kısıtlı alanları) JSON dosyalarından yükleyip
 * OSMDroid MapView üzerine çizen yardımcı sınıf.
 *
 * Not: OSMDroid 6.1.1'de [org.osmdroid.views.overlay.Circle] kaldırılmıştır;
 * daire gösterimi [Polygon.pointsAsCircle] ile yapılır.
 */
class MapHelper(private val context: Context, private val mapView: MapView) {

    /** Altın noktalarını assets/locations.json'dan yükleyip haritaya marker olarak ekler. */
    fun loadGoldLocations(onLocationClick: (GoldLocation) -> Unit): List<GoldLocation> {
        val locations = mutableListOf<GoldLocation>()
        try {
            val arr = JSONArray(readAsset("locations.json"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val loc = GoldLocation(
                    id = obj.optInt(GoldLocation.KEY_ID, i + 1),
                    name = obj.getString(GoldLocation.KEY_NAME),
                    lat = obj.getDouble(GoldLocation.KEY_LAT),
                    lon = obj.getDouble(GoldLocation.KEY_LON),
                    elevationM = obj.optString(
                        GoldLocation.KEY_ELEVATION,
                        context.getString(R.string.label_unknown)
                    ),
                    terrain = obj.optString(
                        GoldLocation.KEY_TERRAIN,
                        context.getString(R.string.label_unknown)
                    ),
                    distanceKm = obj.optDouble(GoldLocation.KEY_DISTANCE, 0.0),
                    description = obj.optString(GoldLocation.KEY_DESCRIPTION, "")
                )
                locations.add(loc)
                addGoldMarker(loc, onLocationClick)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return locations
    }

    /** Kısıtlı alanları assets/restricted_areas.json'dan yükleyip haritaya çizer. */
    fun loadRestrictedAreas(onAreaClick: (RestrictedArea) -> Unit): List<RestrictedArea> {
        val areas = mutableListOf<RestrictedArea>()
        try {
            val arr = JSONArray(readAsset("restricted_areas.json"))
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val area = RestrictedArea(
                    id = obj.optInt(RestrictedArea.KEY_ID, i + 101),
                    name = obj.getString(RestrictedArea.KEY_NAME),
                    lat = obj.getDouble(RestrictedArea.KEY_LAT),
                    lon = obj.getDouble(RestrictedArea.KEY_LON),
                    radiusM = obj.getDouble(RestrictedArea.KEY_RADIUS),
                    type = obj.optString(RestrictedArea.KEY_TYPE, "Kısıtlı"),
                    description = obj.optString(RestrictedArea.KEY_DESCRIPTION, "")
                )
                areas.add(area)
                addRestrictedPolygon(area, onAreaClick)
                addRestrictedMarker(area, onAreaClick)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return areas
    }

    private fun addGoldMarker(loc: GoldLocation, onLocationClick: (GoldLocation) -> Unit) {
        val marker = Marker(mapView)
        marker.position = GeoPoint(loc.lat, loc.lon)
        marker.title = loc.name
        marker.snippet = buildString {
            append(context.getString(R.string.label_elevation))
            append(": ")
            append(loc.elevationM)
            append(" m | ")
            append(context.getString(R.string.label_terrain))
            append(": ")
            append(loc.terrain)
        }
        marker.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_gold_pin))
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.setOnMarkerClickListener { _, _ ->
            onLocationClick(loc)
            true // true = olay tüketildi, varsayılan info window açılmasın
        }
        mapView.overlayManager.add(marker)
    }

    private fun addRestrictedMarker(area: RestrictedArea, onAreaClick: (RestrictedArea) -> Unit) {
        val marker = Marker(mapView)
        marker.position = GeoPoint(area.lat, area.lon)
        marker.title = area.name
        marker.snippet = area.type
        marker.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_restricted_warning))
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.setOnMarkerClickListener { _, _ ->
            onAreaClick(area)
            true
        }
        mapView.overlayManager.add(marker)
    }

    /** OSMDroid 6.1'de Circle kaldırıldığı için daire Polygon olarak çizilir. */
    private fun addRestrictedPolygon(area: RestrictedArea, onAreaClick: (RestrictedArea) -> Unit) {
        val center = GeoPoint(area.lat, area.lon)
        val polygon = Polygon(mapView)
        polygon.points = Polygon.pointsAsCircle(center, area.radiusM)
        // Not: setFillColor/setStrokeColor 6.1'de deprecated; Paint API kullanılır.
        polygon.getFillPaint().setColor(Color.parseColor("#662E7D32"))   // yarı saydam yeşil dolgu
        polygon.getOutlinePaint().setColor(Color.parseColor("#2E7D32"))
        polygon.getOutlinePaint().setStrokeWidth(3f)
        polygon.title = area.name
        polygon.snippet = area.type
        // Alanin uzerine tiklaninca da detay diyalogu acilsin
        polygon.setOnClickListener { _, _, _ ->
            onAreaClick(area)
            true
        }
        mapView.overlayManager.add(polygon)
    }

    /** Afyonkarahisar merkezine odaklanır. */
    fun centerOnAfyon() {
        // zoom 12: cevrimdisi harita kapsami (z10-15) ekranin tamamini doldurur
        mapView.controller.setZoom(12.0)
        mapView.controller.setCenter(GeoPoint(AFYON_LAT, AFYON_LON))
    }

    private fun readAsset(name: String): String {
        val inputStream: InputStream = context.assets.open(name)
        val reader = InputStreamReader(inputStream)
        val sb = StringBuilder()
        val buf = CharArray(8192)
        var len = reader.read(buf)
        while (len > 0) {
            sb.append(buf, 0, len)
            len = reader.read(buf)
        }
        inputStream.close()
        return sb.toString()
    }

    companion object {
        // Afyonkarahisar merkezi koordinatı
        const val AFYON_LAT = 38.756886
        const val AFYON_LON = 30.538704
    }
}
