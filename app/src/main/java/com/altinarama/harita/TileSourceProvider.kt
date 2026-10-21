package com.altinarama.harita

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.io.File

/**
 * Harita karo kaynağını yönetir.
 *
 * Öncelik sırası:
 *  1. assets/offline/afyon.mbtiles varsa -> TAMAMEN ÇEVRİMDIŞI harita
 *  2. yoksa -> çevrimiçi OpenStreetMap (MAPNIK), OSMDroid'in kendi önbelleği ile
 *
 * OSMDroid 6.1.1 notu: Arşiv (MBTiles) dosyaları tile source'un basePath'inden değil,
 * [Configuration.setOsmdroidBasePath] ile verilen dizinden taranır. Bu dizine
 * kopyalanan .mbtiles dosyası otomatik olarak kaydedilir.
 */
object TileSourceProvider {

    private const val ASSET_PATH = "offline/afyon.mbtiles"
    private const val TILE_DIR_NAME = "osmdroid_tiles"
    private const val TILE_FILE_NAME = "afyon.mbtiles"
    private const val KEY_LAST_SIZE = "last_asset_size"

    /** Sonucun türü. */
    data class Result(val tileSource: ITileSource, val isOffline: Boolean, val note: String)

    /**
     * Uygulamaya uygun karo kaynağını hazırlar.
     * @param context uygulama bağlamı
     */
    fun prepare(context: Context): Result {
        val tilesDir = File(context.filesDir, TILE_DIR_NAME)
        val target = File(tilesDir, TILE_FILE_NAME)

        // 1) MBTiles varsa osmdroid base path dizinine kopyala
        val hasAsset = copyAssetIfPresent(context, tilesDir, target)

        if (hasAsset) {
            try {
                // Arşiv dosyaları bu dizinden bulunur
                Configuration.getInstance().setOsmdroidBasePath(tilesDir)
                // basePath "http://localhost" -> online saglayici devre disi, yalnizca arsiv kullanilir
                val tileSource = FileBasedTileSource(
                    "AfyonOffline",
                    0, 18, 256, ".png",
                    arrayOf("http://localhost")
                )
                android.util.Log.i(
                    "TileSourceProvider",
                    "OFFLINE | kaynak=${tileSource.name()} | basePath=" +
                        "${Configuration.getInstance().getOsmdroidBasePath()?.absolutePath} | " +
                        "dosya=$target exists=${target.exists()} boyut=${target.length()}"
                )
                // Not: OSMDroid, MapView constructor'inda arsiv dosyalarini (MBTiles) bu
                // dizinden tarar. Bu yuzden prepare() cagrisi setContentView() ONCESI
                // yapilmalidir; aksi halde arsiv listesi bos kalir ve karolar yuklenmez.
                return Result(tileSource, true, "ÇEVRİMDIŞI HARİTA")
            } catch (e: Exception) {
                android.util.Log.e("TileSourceProvider", "OFFLINE HATA", e)
                e.printStackTrace()
            }
        } else {
            android.util.Log.w("TileSourceProvider", "MBTiles bulunamadi -> cevrimici moda dusuluyor")
        }

        // 2) Fallback: çevrimiçi
        return Result(TileSourceFactory.MAPNIK, false, "ÇEVRİMİÇİ HARİTA")
    }

    /**
     * assets içindeki MBTiles dosyasını uygulama dizinine kopyalar.
     *
     * Gereksiz kopyalamayı önlemek için son kopyalanan boyut SharedPreferences'ta tutulur.
     * @return true eğer dosya mevcutsa ve kullanılabilir durumdaysa
     */
    private fun copyAssetIfPresent(context: Context, tilesDir: File, target: File): Boolean {
        val prefs = context.getSharedPreferences("offline_tiles", Context.MODE_PRIVATE)
        return try {
            context.assets.open(ASSET_PATH).use { input ->
                val assetSize = input.available().toLong()
                val lastSize = prefs.getLong(KEY_LAST_SIZE, -1L)

                // Zaten güncel kopyası duruyorsa tekrar kopyalama
                if (target.exists() && target.length() == assetSize && assetSize > 0 && lastSize == assetSize) {
                    return true
                }

                tilesDir.mkdirs()
                val tmp = File(tilesDir, "$TILE_FILE_NAME.tmp")
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
                // Atomik değiştirme
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }

                prefs.edit().putLong(KEY_LAST_SIZE, assetSize).apply()
                android.util.Log.i(
                    "TileSourceProvider",
                    "MBTiles kopyalandi: $target (${target.length()} bayt)"
                )
                true
            }
        } catch (e: Exception) {
            // Dosya yok (normal durum) -> çevrimiçi moda düş
            android.util.Log.w("TileSourceProvider", "MBTiles acilamadi: ${e.message}")
            false
        }
    }
}
