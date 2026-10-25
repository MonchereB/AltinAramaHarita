package com.altinarama.harita

/**
 * Bir altın savakçılığı potansiyeli noktası.
 *
 * Alanlar üç kaynaktan gelir:
 *  - İnternet/nitelik verileri yer alan temel bilgiler (koordinat, rakım, teren)
 *  - Jeoloji kaynakları (MTA, JMO, AFAD, TEMA)  -> kaynak, litoloji, göstergeler, akıl yürütme
 *  - OSM akarsu/yol geometrisi + SoilGrids + Open-Meteo -> diyalog ekranları
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
    /** Kategori: BIRINCIL_KAYNAK / FAY ZONU / PORFIRIK ... */
    val category: String = "",
    /** Bilinen birincil cevher / maden adı */
    val sourceDeposit: String = "",
    /** Bölgeye ait kayaç tipi (litoloji) */
    val lithology: String = "",
    /** Arazide aranacak somut göstergeler (mineraller, yapılar) */
    val indicators: String = "",
    /** Maden mühendisi gözüyle çıkarım */
    val reasoning: String = "",

    // --- Savak uygunluğu / arazi mantığı ---
    /** Uygulama içindeki uydu küçük resminin yolu (assets) */
    val thumb: String = "",
    /** Savak için önerilen uygunluk puanı + açıklaması */
    val panningGrade: String = "",
    val panningWhy: String = "",
    /** Arazide tam olarak nereye kazılacağı (adım adım öneri) */
    val fieldTips: String = "",
    /** Altını bulup bulmadığımızı nasıl anlarız - kontroller */
    val successChecks: String = "",
    /** Daha önce altın bulunmuş mu / nasıl biliniyor */
    val evidence: String = "",
    /** Erişim özet: en yakın yol/köy ve not */
    val access: String = "",

    // --- İnternet kaynaklı ölçümler ---
    /** Kum/çamur/kil oranı (SoilGrids, 0-5 cm) */
    val sandPct: Double = -1.0,
    val clayPct: Double = -1.0,
    val siltPct: Double = -1.0,
    /** Yıllık ortalama yağış (2019-2023, Open-Meteo) */
    val annualPrecipMm: Int = -1,
    /** Bahar (Nisan-Mayıs) yağış payı % */
    val springSharePct: Double = -1.0,
    /** Akarsu akış rejimi: KALICI / MEVSIMLIK / BELIRSIZ */
    val streamRegime: String = "",
    /** Akarsu adı (OSM) */
    val streamName: String = "",
    /** Geometri üzerinde hesaplanan kazı noktaları */
    val digPoints: List<DigPoint> = emptyList()
) {
    /** Bir kazı hedefi — mantık yürütme bulgusu. */
    data class DigPoint(
        val lat: Double,
        val lon: Double,
        /** KAVŞAK / IC_KIYI / BASAMAK */
        val type: String,
        val title: String,
        val why: String,
        val distM: Int
    ) {
        companion object {
            const val CONFLUENCE = "KAVŞAK"
            const val INSIDE_BEND = "IC_KIYI"
        }
    }

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
        const val KEY_THUMB = "thumb"
        const val KEY_PANNING_GRADE = "panning_grade"
        const val KEY_PANNING_WHY = "panning_why"
        const val KEY_FIELD_TIPS = "field_tips"
        const val KEY_SUCCESS_CHECKS = "success_checks"
        const val KEY_EVIDENCE = "evidence"
        const val KEY_ACCESS = "access"
        const val KEY_SOIL = "soil"
        const val KEY_CLIMATE = "climate"
        const val KEY_STREAM_REGIME = "stream_regime"
        const val KEY_STREAM_NAME = "stream_name"
        const val KEY_DIG_POINTS = "dig_points"
    }
}
