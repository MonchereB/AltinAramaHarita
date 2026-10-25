package com.altinarama.harita

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.io.File

/**
 * Harita karo kaynağını yönetir.
 *
 * İKİ ÇEVRİMDIŞI stil + CANLI akış vardır:
 *  - TOPO  : OpenTopoMap karoları      (afyon.mbtiles)     -> vadi/topo/kilometre
 *  - UYDU  : Google uydu karoları      (afyon_sat.mbtiles) -> uydu/fotoğraf (z20'ye kadar)
 *  - CANLI : İnternetli Google uydu akışı (SQL cache kalıcı; gezilen yerler
 *            çevrimdışı ziyaretlerde de gelir)
 *
 * OSMDroid, MapView kurulumunda basePath dizinindeki TÜM arşivleri tarar ve
 * tek isimde karolar her iki arşivden birden çekilebilir; bu yüzden karışmasın
 * diye HER ARŞİV KENDİ DİZİNİNDE Tutulur ve stil değişince basePath değiştirilir:
 *
 *   filesDir/osmdroid_tiles/topo/afyon.mbtiles        (basePath = .../topo)
 *   filesDir/osmdroid_tiles/sat/afyon_sat.mbtiles     (basePath = .../sat)
 *
 * Stil değiştirme -> [STYLE_KEY] preference yazılır -> Activity [recreate()] edilir.
 */
object TileSourceProvider {

    private const val ASSET_TOPO = "offline/afyon.mbtiles"
    private const val ASSET_SAT = "offline/afyon_sat.mbtiles"
    private const val TILE_DIR_NAME = "osmdroid_tiles"
    private const val KEY_LAST_SIZE_PREFIX = "last_asset_size_"

    const val STYLE_TOPO = "topo"
    const val STYLE_SAT = "sat"
    const val STYLE_LIVE = "live"

    /** Sonucun türü. */
    data class Result(val tileSource: ITileSource, val isOffline: Boolean, val note: String)

    /** Aktif stil (tercih edilmiyorsa TOPO). */
    fun currentStyle(context: Context): String =
        context.getSharedPreferences("map_style", Context.MODE_PRIVATE)
            .getString("style", STYLE_TOPO) ?: STYLE_TOPO

    fun setStyle(context: Context, style: String) {
        context.getSharedPreferences("map_style", Context.MODE_PRIVATE)
            .edit().putString("style", style).apply()
    }

    /** Kamera durumunu kaydet (geçiş sonrası aynı konuma dön). */
    fun saveCamera(context: Context, lat: Double, lon: Double, zoom: Double) {
        context.getSharedPreferences("map_style", Context.MODE_PRIVATE)
            .edit()
            .putFloat("cam_lat", lat.toFloat())
            .putFloat("cam_lon", lon.toFloat())
            .putFloat("cam_zoom", zoom.toFloat())
            .apply()
    }

    fun loadCamera(context: Context): Triple<Double, Double, Double>? {
        val prefs = context.getSharedPreferences("map_style", Context.MODE_PRIVATE)
        if (!prefs.contains("cam_lat")) return null
        return Triple(
            prefs.getFloat("cam_lat", 0f).toDouble(),
            prefs.getFloat("cam_lon", 0f).toDouble(),
            prefs.getFloat("cam_zoom", 12.0f).toDouble()
        )
    }

    /**
     * Uygulamaya uygun karo kaynağını hazırlar (MapView OLUŞTURULMADAN ÖNCE çağrılmalı).
     */
    fun prepare(context: Context): Result {
        return try {
        val style = currentStyle(context)
        val isSat = style == STYLE_SAT

        // CANLI stil: hicbir arsiv yuklenmez; Google uydu katmanindan canli akar
        // (osmdroid SQL cache'i gorulen karolari kaydeder -> sonra cevrimdisi de gelir).
        if (style == STYLE_LIVE) {
            val baseDirLive = File(context.filesDir, TILE_DIR_NAME)
            val liveDir = File(baseDirLive, "live")
            liveDir.mkdirs()
            Configuration.getInstance().osmdroidBasePath = liveDir
            val src = buildLiveSource()
            android.util.Log.i("TileSourceProvider", "CANLI stil | kaynak=" + src.name())
            return Result(src, false, "CANLI UYDU (İnternet)")
        }

        val baseDir = File(context.filesDir, TILE_DIR_NAME)
        val styleDir = File(baseDir, if (isSat) "sat" else "topo")
        val assetPath = if (isSat) ASSET_SAT else ASSET_TOPO
        val fileName = if (isSat) "afyon_sat.mbtiles" else "afyon.mbtiles"
        val target = File(styleDir, fileName)

        val ok = copyAssetIfPresent(context, assetPath, styleDir, target,
            KEY_LAST_SIZE_PREFIX + style)
        if (!ok) {
            android.util.Log.w("TileSourceProvider", "$assetPath acilamadi (assets?)")
        }
        if (!target.exists()) {
            // Yedek: diger stilin dosyasina dus (boylece uygulama haritasiz kalmaz)
            val alt = File(baseDir, if (isSat) "topo/afyon.mbtiles" else "sat/afyon_sat.mbtiles")
            if (alt.exists()) {
                styleDir.mkdirs()
                alt.copyTo(File(styleDir, alt.name), overwrite = true)
            }
        }
        if (!target.exists()) {
            // hicbir sey yok -> cevrimici
            return Result(TileSourceFactory.MAPNIK, false, "ÇEVRİMİÇİ HARİTA")
        }

        // v1.2.x'ten gelen atlasik kopya: taban dizindeki dağınık mbtiles'i temizle
        // (stil alt dizinleri devreye girdikten sonra gereksiz ~85 MB yer kaplar)
        if (target.exists()) File(baseDir, "afyon.mbtiles").delete()

        // Arşiv dosyaları bu dizinden bulunur
        Configuration.getInstance().setOsmdroidBasePath(styleDir)
        val name = if (isSat) "AfyonUydu" else "AfyonOffline"
        // UYDU paketi artık z20'ye kadar karoluyor (yeni derin bloklar);
        // TOPO z17'de bitiyor. Zoom tavanını stile göre koy.
        val maxZoom = if (isSat) 20 else 17
        val tileSource = FileBasedTileSource(name, 0, maxZoom, 256, ".png",
            arrayOf("http://localhost"))
        android.util.Log.i("TileSourceProvider",
            "OFFLINE stil=$style | kaynak=$name | basePath=${styleDir.absolutePath} | boyut=${target.length()}")
        Result(tileSource, true,
            if (isSat) "ÇEVRİMDIŞI UYDU" else "ÇEVRİMDIŞI HARİTA")
        } catch (e: Exception) {
            android.util.Log.e("TileSourceProvider", "prepare hata", e)
            Result(TileSourceFactory.MAPNIK, false, "ÇEVRİMİÇİ HARİTA")
        }
    }

    /**
     * CANLI stil kaynağı: Google uydu katmanı (mt*.google.com/vt/lyrs=s).
     * Kırsal Afyon'da z20'ye kadar GERÇEK detay var (~0.15 m/px);
     * Esri z19'da "Map data not yet available" yer tutucusu döndürür.
     * osmdroid'in SQL cache'i indirilen karoları saklar -> gezilen alan
     * sonraki çevrimdışı ziyaretlerde hâlâ görülebilir.
     * Not: doğrudan Google tile endpoint'i kişisel kullanım içindir.
     */
    private fun buildLiveSource(): ITileSource = object : OnlineTileSourceBase(
        "AfyonCanli", 0, 20, 256, ".jpg", arrayOf("https://mt1.google.com/vt/lyrs=s&hl=tr")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://mt${((x + y + z) % 4)}.google.com/vt/lyrs=s&hl=tr&x=$x&y=$y&z=$z"
        }
    }

    /**
     * assets içindeki MBTiles dosyasını stil dizinine kopyalar (boyut değişmişse).
     */
    private fun copyAssetIfPresent(
        context: Context,
        assetPath: String,
        tilesDir: File,
        target: File,
        sizeKey: String
    ): Boolean {
        val prefs = context.getSharedPreferences("offline_tiles", Context.MODE_PRIVATE)
        return try {
            context.assets.open(assetPath).use { input ->
                val assetSize = input.available().toLong()
                val lastSize = prefs.getLong(sizeKey, -1L)

                // Zaten güncel kopyası duruyorsa tekrar kopyalama
                if (target.exists() && target.length() == assetSize && assetSize > 0 && lastSize == assetSize) {
                    return true
                }
                tilesDir.mkdirs()
                val tmp = File(tilesDir, "${target.name}.tmp")
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                prefs.edit().putLong(sizeKey, assetSize).apply()
                android.util.Log.i("TileSourceProvider",
                    "MBTiles kopyalandi: $target (${target.length()} bayt)")
                true
            }
        } catch (e: Exception) {
            android.util.Log.w("TileSourceProvider", "MBTiles acilamadi: ${e.message}")
            false
        }
    }
}
