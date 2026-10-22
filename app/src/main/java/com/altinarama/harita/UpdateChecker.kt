package com.altinarama.harita

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
 *  2. Sürüm kodu karşılaştırılır (etiketten hesaplanır - hızlı)
 *  3. Kullanıcı onayıyla APK indirilir (yüzde ilerleme ile)
 *  4. İndirilen APK doğrulanır (PackageManager ile)
 *  5. Android'un sistem kurulum ekranı açılır
 *  6. Kurulum sonrası uygulama yeniden başlatılır (UpdateReceiver)
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
        val versionName: String,
        val publishedAt: String
    )

    // ------------------------------------------------------------------
    // Kurulu sürüm bilgisi
    // ------------------------------------------------------------------

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

    /** Kurulu sürümün versionName değeri (örn. "1.1.0"). */
    fun installedVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /**
     * Uygulamanın "bilinmeyen kaynaklardan kurma" izni var mı?
     * Android 8.0+ için gereklidir.
     */
    fun canRequestPackageInstalls(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    } catch (e: Exception) {
        true
    }

    /** "Bilinmeyen kaynaklardan uygulama kurma" ayarlarını açar. */
    fun openInstallSettings(context: Context): Boolean = try {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e2: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Sürüm kontrolü
    // ------------------------------------------------------------------

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
                    result = parseRelease(JSONObject(body))
                    Log.i(TAG, "Guncel surum: ${result?.tagName} (versionCode=${result?.versionCode})")
                } else {
                    error = "GitHub yanıtı: ${conn.responseCode}"
                    Log.w(TAG, "GitHub API hatasi: ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "Bağlantı hatası"
                Log.w(TAG, "Kontrol basarisiz: ${e.message}")
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                callback(result, error)
            }
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
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

        // Surum kodunu etiketten hesapla (hizli, 85 MB indirme gerekmez)
        val versionName = tag.removePrefix("v")
        val versionCode = versionCodeFromTag(tag) ?: 0

        return ReleaseInfo(
            tagName = tag,
            name = json.optString("name", tag),
            body = cleanMarkdown(json.optString("body", "")),
            apkUrl = apkUrl,
            versionCode = versionCode,
            versionName = versionName,
            publishedAt = json.optString("published_at", "")
        )
    }

    /** Sürüm notlarındaki markdown işaretlerini temizler (düz metin gösterim için). */
    private fun cleanMarkdown(text: String): String = text
        .replace(Regex("(?m)^#{1,6}\\s*"), "")      // başlıklar
        .replace("**", "")                            // kalın
        .replace("__", "")
        .replace("*", "")                             // italik / madde
        .replace("`", "")                             // kod
        .replace(Regex("(?m)^[-•]\\s*"), "• ")         // madde isaretleri
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    /**
     * Release etiketinden versionCode hesaplar.
     * CI ile ayni formül: major*100 + minor*10 + patch (v1.2.3 -> 123)
     */
    private fun versionCodeFromTag(tag: String): Int? {
        val m = Regex("""v?(\d+)\.(\d+)\.(\d+)""").find(tag) ?: return null
        val (major, minor, patch) = m.destructured
        return major.toInt() * 100 + minor.toInt() * 10 + patch.toInt()
    }

    // ------------------------------------------------------------------
    // İndirme
    // ------------------------------------------------------------------

    /**
     * APK'yı indirir.
     * @param onProgress (yüzde 0-100, indirilen bayt) - ana iş parçacığında çağrılır
     * @param onDone (dosya, hata) - dosya null ise hata oluşmuştur
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int, Long) -> Unit,
        onDone: (File?, String?) -> Unit
    ) {
        Executors.newSingleThreadExecutor().execute {
            var error: String? = null
            var file: File? = null
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads")
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, "AltinAramaHarita-yeni.apk")
                // Onceki yarim dosyayi temizle
                if (target.exists()) target.delete()

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
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    android.os.Handler(android.os.Looper.getMainLooper())
                                        .post { onProgress(percent, downloaded) }
                                }
                            } else {
                                android.os.Handler(android.os.Looper.getMainLooper())
                                    .post { onProgress(-1, downloaded) }
                            }
                        }
                    }
                }
                conn.disconnect()

                // --- Dogrulama: gercekten gecerli bir APK mi? ---
                if (target.length() < 100000) {
                    error = "Dosya beklenenden küçük (${target.length()} bayt)"
                    target.delete()
                } else {
                    val info = context.packageManager.getPackageArchiveInfo(
                        target.absolutePath, 0
                    )
                    if (info == null) {
                        error = "APK okunamadı"
                        target.delete()
                    } else {
                        val remoteCode = packageVersionCode(context, target.absolutePath) ?: 0
                        if (remoteCode <= installedVersionCode(context)) {
                            error = "İndirilen sürüm kurulu sürümden eski"
                            target.delete()
                        } else {
                            file = target
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "İndirme hatası"
                Log.e(TAG, "Indirme hatasi", e)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onDone(file, error)
            }
        }
    }

    /** Verilen APK dosyasindaki versionCode degerini okur. */
    fun packageVersionCode(context: Context, apkPath: String): Int? = try {
        val info = context.packageManager.getPackageArchiveInfo(apkPath, 0)
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

    // ------------------------------------------------------------------
    // Kurulum
    // ------------------------------------------------------------------

    /**
     * İndirilen APK'yı sistem kurulum ekranında açar.
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
