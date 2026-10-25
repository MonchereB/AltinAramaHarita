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
                    description = obj.optString(GoldLocation.KEY_DESCRIPTION, ""),
                    rank = obj.optInt(GoldLocation.KEY_RANK, 0),
                    confidence = obj.optString(GoldLocation.KEY_CONFIDENCE, ""),
                    category = obj.optString(GoldLocation.KEY_CATEGORY, ""),
                    sourceDeposit = obj.optString(GoldLocation.KEY_SOURCE, ""),
                    lithology = obj.optString(GoldLocation.KEY_LITHOLOGY, ""),
                    indicators = obj.optString(GoldLocation.KEY_INDICATORS, ""),
                    reasoning = obj.optString(GoldLocation.KEY_REASONING, ""),
                    thumb = obj.optString(GoldLocation.KEY_THUMB, ""),
                    panningGrade = obj.optString(GoldLocation.KEY_PANNING_GRADE, ""),
                    panningWhy = obj.optString(GoldLocation.KEY_PANNING_WHY, ""),
                    fieldTips = obj.optString(GoldLocation.KEY_FIELD_TIPS, ""),
                    successChecks = obj.optString(GoldLocation.KEY_SUCCESS_CHECKS, ""),
                    evidence = obj.optString(GoldLocation.KEY_EVIDENCE, ""),
                    access = obj.optString(GoldLocation.KEY_ACCESS, ""),
                    sandPct = obj.optJSONObject(GoldLocation.KEY_SOIL).let {
                        if (it == null) -1.0 else it.optDouble("sand", -1.0)
                    },
                    clayPct = obj.optJSONObject(GoldLocation.KEY_SOIL).let {
                        if (it == null) -1.0 else it.optDouble("clay", -1.0)
                    },
                    siltPct = obj.optJSONObject(GoldLocation.KEY_SOIL).let {
                        if (it == null) -1.0 else it.optDouble("silt", -1.0)
                    },
                    annualPrecipMm = obj.optJSONObject(GoldLocation.KEY_CLIMATE).let {
                        if (it == null) -1 else it.optInt("annual_precip_mm", -1)
                    },
                    springSharePct = obj.optJSONObject(GoldLocation.KEY_CLIMATE).let {
                        if (it == null) -1.0 else it.optDouble("spring_share_pct", -1.0)
                    },
                    streamRegime = obj.optString(GoldLocation.KEY_STREAM_REGIME, ""),
                    streamName = obj.optString(GoldLocation.KEY_STREAM_NAME, ""),
                    digPoints = parseDigPoints(obj.optJSONArray(GoldLocation.KEY_DIG_POINTS))
                )
                locations.add(loc)
                addGoldMarker(loc, onLocationClick)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return locations
    }

    /** locations.json'daki dig_points listesini açar. */
    private fun parseDigPoints(arr: JSONArray?): List<GoldLocation.DigPoint> {
        if (arr == null) return emptyList()
        val out = mutableListOf<GoldLocation.DigPoint>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                GoldLocation.DigPoint(
                    lat = o.optDouble("lat", 0.0),
                    lon = o.optDouble("lon", 0.0),
                    type = o.optString("type", "IC_KIYI"),
                    title = o.optString("title", ""),
                    why = o.optString("why", ""),
                    distM = o.optInt("dist", 0)
                )
            )
        }
        return out
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
                addRestrictedPolygon(area)
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
        marker.title = buildString {
            if (loc.rank > 0) append("#").append(loc.rank).append("  ")
            append(loc.name)
        }
        marker.snippet = buildString {
            if (loc.confidence.isNotBlank()) {
                append(loc.confidence)
                append(" • ")
            }
            append(context.getString(R.string.label_elevation))
            append(": ")
            append(loc.elevationM)
            append(" m")
            if (loc.sourceDeposit.isNotBlank()) {
                append(" | ")
                append(loc.sourceDeposit)
            }
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
    private fun addRestrictedPolygon(area: RestrictedArea) {
        val center = GeoPoint(area.lat, area.lon)
        val polygon = Polygon(mapView)
        polygon.points = Polygon.pointsAsCircle(center, area.radiusM)
        // Not: setFillColor/setStrokeColor 6.1'de deprecated; Paint API kullanılır.
        polygon.getFillPaint().setColor(Color.parseColor("#2E2E7D32"))   // çok hafif yeşil dolgu (harita okunabilir kalsın)
        polygon.getOutlinePaint().setColor(Color.parseColor("#2E7D32"))
        polygon.getOutlinePaint().setStrokeWidth(4f)
        polygon.title = area.name
        polygon.snippet = area.type
        // Alan yalnızca GÖRÜNTÜ amaçlı: tıklanınca bilgi penceresi açılmasın.
        // Sadece merkezdeki uyarı işareti (Marker) tıklanabilirdir.
        polygon.setOnClickListener { _, _, _ -> true }
        mapView.overlayManager.add(polygon)
    }

    /** Geçici kazı/hedef işareti koyar (diyalogdaki "GÖSTER" butonu için). */
    fun showDigPointMarker(dp: GoldLocation.DigPoint) {
        val marker = Marker(mapView)
        marker.position = GeoPoint(dp.lat, dp.lon)
        marker.title = dp.title
        marker.snippet = dp.why
        marker.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_dig_point))
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        // Aynı noktada eski geçici işaret varsa temizle
        _digMarkers.forEach { mapView.overlayManager.remove(it) }
        _digMarkers.clear()
        _digMarkers.add(marker)
        mapView.overlayManager.add(marker)
        marker.showInfoWindow()
        mapView.controller.animateTo(GeoPoint(dp.lat, dp.lon))
        // UYDU/CANLI stilinde kazii noktalari z18-20 derin bloklardadir -> z19'a inmeli;
        // TOPO paketi z17'de bittigi icin orada z17 yeterli.
        val targetZoom = when (TileSourceProvider.currentStyle(context)) {
            TileSourceProvider.STYLE_TOPO -> 17.0
            else -> 19.0
        }
        mapView.controller.setZoom(targetZoom)
        mapView.invalidate()
    }

    private val _digMarkers = mutableListOf<Marker>()

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
