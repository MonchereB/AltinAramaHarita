package com.altinarama.harita

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * GitHub Releases üzerinden otomatik güncelleme denetleyicisi.
 *
 * Akış:
 *  1. GitHub Releases API'den en son sürüm sorgulanır
 *  2. İndirilecek APK'nın sürüm kodu, kurulu sürümle karşılaştırılır
 *  3. Yeni sürüm varsa APK indirilir ve kullanıcı onayıyla kurulur
 *
 * Güvenlik: Yalnızca [GITHUB_OWNER]/[GITHUB_REPO] deposundaki sürümler
 * değerlendirilir, indirme HTTPS üzerinden yapılır ve kurulum için
 * Android'un kendi sistem onay penceresi gösterilir.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"

    /** Depo bilgisi - kendi GitHub hesabınıza göre güncelleyin. */
    const val GITHUB_OWNER = "MonchereB"
    const val GITHUB_REPO = "AltinAramaHarita"

    private const val RELEASES_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** Sürüm bilgisi. */
    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val apkUrl: String,
        val versionCode: Int,
        val publishedAt: String
    )

    /** Kurulu sürümün versionCode değeri. */
    fun installedVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }

    /**
     * GitHub'daki en son sürümü sorgular.
     * @param callback (release, hataMesaji) - release null ise hata oluşmuştur
     */
    fun checkLatest(context: Context, callback: (ReleaseInfo?, String?) -> Unit) {
        Executors.newSingleThreadExecutor().execute {
            var result: ReleaseInfo? = null
            var error: String? = null
            try {
                val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AltinAramaHarita/${installedVersionCode(context)}")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    result = parseRelease(context, JSONObject(body))
                    Log.i(TAG, "Guncel surum bulundu: ${result?.tagName} (versionCode=${result?.versionCode})")
                } else {
                    error = "GitHub yanıtı: ${conn.responseCode}"
                    Log.w(TAG, "GitHub API hatasi: ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "Guncelleme kontrolu basarisiz: ${e.message}")
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(result, error)
            }
        }
    }

    private fun parseRelease(context: Context, json: JSONObject): ReleaseInfo? {
        val tag = json.optString("tag_name", "")
        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val n = a.optString("name", "")
            if (n.endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url", "")
                break
            }
        }
        if (apkUrl.isEmpty()) return null

        // APK'nin icindeki gercek versionCode'u okuyalim
        val remoteVersion = readVersionCodeFromUrl(context, apkUrl) ?: 0

        return ReleaseInfo(
            tagName = tag,
            name = json.optString("name", tag),
            body = json.optString("body", ""),
            apkUrl = apkUrl,
            versionCode = remoteVersion,
            publishedAt = json.optString("published_at", "")
        )
    }

    /**
     * Uzak APK'yi indirip versionCode'unu okur.
     * Dosya aynı zamanda indirme klasorune yazilir.
     */
    private fun readVersionCodeFromUrl(context: Context, apkUrl: String): Int? {
        return try {
            val tmp = File(context.cacheDir, "update-check.apk")
            downloadFile(apkUrl, tmp)
            packageVersionCode(context, tmp.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Uzak versionCode okunamadi: ${e.message}")
            null
        }
    }

    /** Verilen adrese dosyayı indirir. */
    private fun downloadFile(urlString: String, target: File) {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 30000
            setRequestProperty("User-Agent", "AltinAramaHarita/Updater")
        }
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    input.copyTo(out)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Verilen APK dosyasindaki versionCode degerini okur. */
    fun packageVersionCode(context: Context, apkPath: String): Int? = try {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apkPath, 0)
        if (info == null) {
            null
        } else {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                info.versionCode
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * APK'yi indirir.
     * @param onProgress (yuzde) - arayuzden çağrılır
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onDone: (File?, String?) -> Unit
    ) {
        Executors.newSingleThreadExecutor().execute {
            var error: String? = null
            var file: File? = null
            try {
                // Uygulamaya ozel harici depolama - ek izin gerektirmez (API 29+ guvenli)
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "AltinAramaHarita-yeni.apk")

                val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "AltinAramaHarita/Updater")
                    doInput = true
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input: InputStream ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastPercent = -1
                        while (input.read(buf).also { read = it } > 0) {
                            out.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    android.os.Handler(android.os.Looper.getMainLooper())
                                        .post { onProgress(percent) }
                                }
                            }
                        }
                    }
                }
                conn.disconnect()
                file = target
            } catch (e: Exception) {
                error = e.message ?: "İndirme hatası"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onDone(file, error)
            }
        }
    }

    /**
     * İndirilen APK'yı kurulum ekranında açar.
     * Android sistem onay penceresini gösterir.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kurulum baslatilamadi", e)
            false
        }
    }
}
