package com.altinarama.harita

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Yer yüksekliği (elevation) sağlayıcısı.
 *
 * Öncelikle assets/locations.json içindeki "elevation_m" alanındaki YAKLAŞIK değeri döner.
 * Cihaz online olduğunda istenirse Open-Elevation API'den gerçek zamanlı değer alınabilir
 * (internet gerektirir, offline ortamda çalışmaz).
 */
object ElevationProvider {

    private const val OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup?locations=%f,%f"

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * JSON'dan gelen kaydedilmiş (yaklaşık) irtifa değerini döner.
     * Bu değer kesin değildir, saha ölçümleriyle doğrulanmalıdır.
     */
    fun getSavedElevation(loc: GoldLocation): String = loc.elevationM

    /**
     * Online olduğunda Open-Elevation API'den irtifa çeker.
     * Callback ana iş parçacığında çağrılır.
     */
    fun fetchOnlineElevation(context: Context, lat: Double, lon: Double, callback: (Double?) -> Unit) {
        Executors.newSingleThreadExecutor().execute {
            var result: Double? = null
            var failed = false
            try {
                val url = URL(String.format(OPEN_ELEVATION_URL, lat, lon))
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode == 200) {
                    val inputStream: InputStream = conn.inputStream
                    val reader = InputStreamReader(inputStream)
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    var len = reader.read(buf)
                    while (len > 0) {
                        sb.append(buf, 0, len)
                        len = reader.read(buf)
                    }
                    inputStream.close()
                    conn.disconnect()

                    val obj = JSONObject(sb.toString())
                    val results = obj.getJSONArray("results")
                    if (results.length() > 0) {
                        result = results.getJSONObject(0).getDouble("elevation")
                    }
                } else {
                    conn.disconnect()
                    failed = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                failed = true
            }

            mainHandler.post {
                if (failed) {
                    Toast.makeText(
                        context,
                        "İrtifa verisi çevrimiçi alınamadı (kayıtlı değer gösteriliyor).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                callback(result)
            }
        }
    }
}
