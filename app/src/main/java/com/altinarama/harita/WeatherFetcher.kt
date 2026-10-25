package com.altinarama.harita

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-Meteo ücretsiz tahmin API'sinden (anahtar gerektirmez) seçilen noktanın
 * anlık hava durumu + 3 günlük yağış özetini getirir.
 *
 * Tamamı ÇEVRİMİÇİ bir özelliktir: internet yoksa geri çağrı null alır ve
 * diyalog "İnternet yok" mesajı gösterir (uygulamanın çekirdeği etkilenmez).
 */
object WeatherFetcher {

    private const val TAG = "WeatherFetcher"

    /**
     * Şu metni üretir:
     *   Şimdi: 21°C, açık. Bugün yağış: 0.4 mm.
     *   3 gün: Yağışlı (12 mm) — savak çalışmalarını erteleyin.
     */
    fun fetch(lat: Double, lon: Double, callback: (String?) -> Unit) {
        val t = Thread {
            var result: String? = null
            try {
                val url = URL(
                    ("https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f" +
                            "&current=temperature_2m,precipitation,weather_code" +
                            "&daily=precipitation_sum,temperature_2m_max,temperature_2m_min" +
                            "&forecast_days=4&timezone=auto").format(lat, lon)
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 7000
                conn.readTimeout = 7000
                conn.setRequestProperty("User-Agent", "AltinAramaHarita/1.3")
                try {
                    val body = conn.inputStream.bufferedReader().readText()
                    result = format(body)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "hava durumu alinamadi: ${e.message}")
            }
            callback(result)
        }
        t.isDaemon = true
        t.start()
    }

    private fun format(body: String): String? {
        return try {
        val root = JSONObject(body)
        val cur = root.optJSONObject("current") ?: return null
        val daily = root.optJSONObject("daily") ?: return null

        val temp = cur.optDouble("temperature_2m", Double.NaN)
        val curPrecip = cur.optDouble("precipitation", 0.0)
        val desc = describeCode(cur.optInt("weather_code", -1))

        val times = daily.optJSONArray("time") ?: return null
        val precs = daily.optJSONArray("precipitation_sum") ?: return null
        val tmax = daily.optJSONArray("temperature_2m_max") ?: return null
        val tmin = daily.optJSONArray("temperature_2m_min") ?: return null

        // idx 0 = bugün; toplam 0..3 gün yağışı hesapla
        var total = 0.0
        for (i in 0 until minOf(4, precs.length())) {
            total += precs.optDouble(i, 0.0)
        }
        val durations = buildString {
            for (i in 1 until minOf(4, times.length())) {
                val p = precs.optDouble(i, 0.0)
                if (i > 1) {
                    append(", ")
                } else if (i == 1) {
                    append("Gelecek: ")
                }
                append("${times.optString(i).substring(5)}: ${tmin.optDouble(i, 0.0).toInt()}⇒${tmax.optDouble(i, 0.0).toInt()}°C, yağış ${"%.1f".format(p)} mm")
            }
        }
        if (durations.isBlank()) { /* beklenmedik boş yanıt — sadece anlık bilgi */ }

        buildString {
            append("Şimdi: ")
            if (!temp.isNaN()) append("${temp.toInt()}°C, ")
            append(desc.lowercase()).append("; anlık yağış ").append("%.1f".format(curPrecip)).append(" mm.\n")
            if (durations.isNotBlank()) { append(durations); append("\n") }
            append(
                if (total >= 6.0) "UYARI: 3 günde ~${"%.0f".format(total)} mm yağış bekleniyor — dere kabarır, savak çalışması güçleşir."
                else "Yağış beklentisi düşük — dere sebebi sakin, savak için uygun görünüm."
            )
        }
        } catch (e: Exception) {
            Log.w(TAG, "parse hatasi: ${e.message}")
            null
        }
    }

    /** WMO kodundan kısa Türkçe açıklama. */
    private fun describeCode(code: Int): String = when (code) {
        0 -> "açık"
        1, 2 -> "parçalı bulutlu"
        3 -> "kapalı"
        45, 48 -> "sisli"
        51, 53, 55, 71, 73, 75, 80, 81, 82 -> "yağışlı"
        95, 96, 99 -> "sağanak/gök gürültülü"
        in 61..67 -> "yağmurlu"
        in 66..67 -> "yağmurlu (dondurucu)"
        else -> "belirsiz"
    }
}
