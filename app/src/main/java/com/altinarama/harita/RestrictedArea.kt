package com.altinarama.harita

/**
 * Yasal olarak kısıtlanmış / yasaklı bir alanı temsil eder
 * (milli park, tabiat parkı, askeri bölge vb.).
 *
 * Merkez koordinatı ve yarıçapı (metre) YAKLAŞIK olup kesin sınırlar
 * ilgili kurumdan öğrenilmelidir.
 */
data class RestrictedArea(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val type: String,
    val description: String
) {
    companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_RADIUS = "radius_m"
        const val KEY_TYPE = "type"
        const val KEY_DESCRIPTION = "description"
    }
}
