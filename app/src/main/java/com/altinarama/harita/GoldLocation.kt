package com.altinarama.harita

/**
 * Altın savakçılığı (plase altın) potansiyeli taşıyan bir dere/akarsu noktası.
 *
 * Koordinatlar ve nitelikler jeolojik araştırmaya dayalıdır; ancak
 * "yaklaşık/referans" niteliktedir. Kesin maden statüsü için MTA'dan teyit gerekir.
 */
data class GoldLocation(
    val id: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevationM: String,
    val terrain: String,
    val distanceKm: Double,
    val description: String,

    // --- Jeolojik zenginlik alanlari ---
    /** Sıralama: 1 = en yüksek olasılık */
    val rank: Int = 0,
    /** Kanıt gücü: "Çok yüksek" / "Yüksek" / "Orta" / "Düşük" */
    val confidence: String = "",
    /** Kategori: BIRINCIL_KAYNAK / YUKSEK / ORTA / DUSUK / REFERANS */
    val category: String = "",
    /** Bilinen birincil cevher / maden adı */
    val sourceDeposit: String = "",
    /** Bölgeye ait kayaç tipi (litoloji) */
    val lithology: String = "",
    /** Arazide aranacak somut göstergeler (mineraller, yapılar) */
    val indicators: String = "",
    /** Maden mühendisi gözüyle çıkarım */
    val reasoning: String = ""
) {
    companion object {
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_ELEVATION = "elevation_m"
        const val KEY_TERRAIN = "terrain"
        const val KEY_DISTANCE = "distance_km"
        const val KEY_DESCRIPTION = "description"
        const val KEY_RANK = "rank"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_CATEGORY = "category"
        const val KEY_SOURCE = "source_deposit"
        const val KEY_LITHOLOGY = "lithology"
        const val KEY_INDICATORS = "indicators"
        const val KEY_REASONING = "reasoning"
    }
}
