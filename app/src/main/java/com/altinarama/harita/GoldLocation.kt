package com.altinarama.harita

/**
 * Altın panning (savakçılığı) potansiyeli olan bir dere/akarsu noktasını temsil eder.
 *
 * Not: Tüm koordinatlar ve nitelikler YAKLAŞIK/referans olup kesin maden statüsü
 * için MTA'dan teyit gerekir.
 */
data class GoldLocation(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevationM: String,
    val terrain: String,
    val distanceKm: Double,
    val description: String
) {
    companion object {
        // JSON anahtarları
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_ELEVATION = "elevation_m"
        const val KEY_TERRAIN = "terrain"
        const val KEY_DISTANCE = "distance_km"
        const val KEY_DESCRIPTION = "description"
    }
}
