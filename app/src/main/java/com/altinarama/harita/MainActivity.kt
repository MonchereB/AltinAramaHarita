package com.altinarama.harita

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var mapHelper: MapHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Uygulama guncellendiyse (acilis sirasinda surum degistiyse) haberdar et
        detectUpdateOnStart()

        // OSMDroid yapılandırması (MapView inflate edilmeden önce yapılmalı)
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        // OSMDroid teşhis logları (karo arşivden geliyor mu?)
        Configuration.getInstance().isDebugMode = DEBUG_MODE

        // ⚠️ KRİTİK: Karo kaynağı ve arşiv dizini MapView OLUŞTURULMADAN ÖNCE
        // ayarlanmalıdır. OSMDroid, MapView constructor'ında arşiv dosyalarını
        // (MBTiles) osmdroid base path dizininden tarar ve o anki listeyi
        // kilitler. setTileSource() sonradan bu listeyi yenilemez.
        val tileResult = TileSourceProvider.prepare(this)

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(tileResult.tileSource)
        findViewById<TextView>(R.id.tvLayerInfo).text = buildString {
            append(tileResult.note)
            append("  |  ")
            append(getString(R.string.layer_gold))
        }
        // Not: setBuiltInZoomControls 6.1'de deprecated; ZoomController kullanılır.
        mapView.getZoomController().setVisibility(
            org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
        )
        mapView.setMultiTouchControls(true)
        // ⚠️ KRİTİK: 256px karoları cihaz DPI'sına göre büyütmek (DPI ölçekleme)
        // 2.6x büyütme = bulanık harita. Kapatınca karolar 1:1 çizilir, net olur.
        mapView.setTilesScaledToDpi(false)

        // Pusula ve ölçek çubuğu
        val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay.enableCompass()
        mapView.overlayManager.add(compassOverlay)
        mapView.overlayManager.add(ScaleBarOverlay(mapView))

        // Konum overlay'i
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        mapView.overlayManager.add(myLocationOverlay)

        // Yardımcı sınıf
        mapHelper = MapHelper(this, mapView)

        // İzinleri iste
        requestPermissionsIfNeeded()

        // Verileri yükle ve çiz
        mapHelper.loadGoldLocations { loc ->
            showLocationInfoDialog(loc)
        }
        mapHelper.loadRestrictedAreas { area ->
            showRestrictedInfoDialog(area)
        }

        // Başlangıç: Afyonkarahisar merkezi
        mapHelper.centerOnAfyon()

        // Düğmeler
        findViewById<Button>(R.id.btnCenterMyLocation).setOnClickListener {
            goToMyLocation()
        }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.btnMenu
        ).setOnClickListener {
            showMenu()
        }
    }

    // ------------------------------------------------------------------
    // Menü
    // ------------------------------------------------------------------

    /** Alt menü (⋮) - en altında sürüm numarası. */
    private fun showMenu() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.menu_sheet, null)

        val installedCode = UpdateChecker.installedVersionCode(this)
        val installedName = UpdateChecker.installedVersionName(this)

        // Menü en altında sürüm numarası
        view.findViewById<TextView>(R.id.menuVersion).text =
            "Sürüm v$installedName  •  build $installedCode"

        // "Sürüm Güncelleme" -> kontrol başlat
        view.findViewById<View>(R.id.menuUpdate).setOnClickListener {
            sheet.dismiss()
            checkForUpdateFromMenu()
        }

        // Yasal uyarı
        view.findViewById<View>(R.id.menuLegal).setOnClickListener {
            sheet.dismiss()
            showLegalNoticeDialog()
        }

        // Çevrimdışı harita bilgisi
        view.findViewById<View>(R.id.menuOfflineInfo).setOnClickListener {
            sheet.dismiss()
            showOfflineInfoDialog()
        }

        sheet.setContentView(view)
        sheet.show()
    }

    // ------------------------------------------------------------------
    // Güncelleme
    // ------------------------------------------------------------------

    /**
     * Menüden "Sürüm Güncelleme" seçildiğinde çalışır.
     * Sürüm güncelse bilgilendirir, değilse güncelleme seçeneğini sunar.
     */
    private fun checkForUpdateFromMenu() {
        val progress = AlertDialog.Builder(this)
            .setTitle(R.string.update_checking_title)
            .setMessage(R.string.update_checking)
            .setCancelable(false)
            .create()
        progress.show()

        val installedCode = UpdateChecker.installedVersionCode(this)
        val installedName = UpdateChecker.installedVersionName(this)

        UpdateChecker.checkLatest(this) { release, error ->
            progress.dismiss()
            if (release == null) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.update_checking_title)
                    .setMessage(getString(R.string.update_check_failed, error ?: "?"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@checkLatest
            }

            if (release.versionCode <= 0 || release.versionCode <= installedCode) {
                // Sürüm güncel
                AlertDialog.Builder(this)
                    .setTitle(R.string.update_current_title)
                    .setMessage(getString(R.string.update_current_msg, installedName))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                // Yeni sürüm var -> güncelleme seçeneğini sun
                showUpdateAvailableDialog(release, installedCode)
            }
        }
    }

    private fun showUpdateAvailableDialog(
        release: UpdateChecker.ReleaseInfo,
        installedCode: Int
    ) {
        val notes = release.body.take(600).ifBlank { "—" }
        val msg = getString(
            R.string.update_available_msg,
            installedCode,
            release.versionCode,
            release.tagName,
            notes
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(R.string.update_download_btn) { _, _ ->
                startUpdateDownload(release)
            }
            .setNegativeButton(R.string.update_later_btn, null)
            .show()
    }

    /** APK'yı yüzde göstergesiyle indirir, doğrular ve kuruluma başlatır. */
    private fun startUpdateDownload(release: UpdateChecker.ReleaseInfo) {
        // Android 8+ için "bilinmeyen kaynak" izni şart
        if (!UpdateChecker.canRequestPackageInstalls(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.update_need_permission_title)
                .setMessage(R.string.update_need_permission_msg)
                .setPositiveButton(R.string.update_open_settings) { _, _ ->
                    UpdateChecker.openInstallSettings(this)
                }
                .setNegativeButton(R.string.update_later_btn, null)
                .show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_update_progress, null)
        val titleView = view.findViewById<TextView>(R.id.updateProgressTitle)
        val bar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val percentView = view.findViewById<TextView>(R.id.updateProgressPercent)
        val detailView = view.findViewById<TextView>(R.id.updateProgressDetail)

        titleView.text = getString(R.string.update_downloading, 0)
        percentView.text = "0%"
        detailView.text = ""

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()

        UpdateChecker.downloadApk(
            this,
            release.apkUrl,
            onProgress = { percent, downloaded ->
                if (percent >= 0) {
                    bar.isIndeterminate = false
                    bar.progress = percent
                    percentView.text = "$percent%"
                    titleView.text = getString(R.string.update_downloading, percent)
                } else {
                    bar.isIndeterminate = true
                    percentView.text = ""
                    detailView.text = "İndirildi: ${downloaded / 1048576} MB"
                }
            },
            onDone = { file, error ->
                if (file == null) {
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        getString(R.string.update_download_failed, error ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@downloadApk
                }
                // Doğrulama tamam -> kurulumu başlat
                titleView.text = getString(R.string.update_verifying)
                bar.isIndeterminate = true
                percentView.text = ""

                if (!UpdateChecker.installApk(this, file)) {
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        getString(R.string.update_install_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    return@downloadApk
                }
                // Sistem kurulum ekranı açıldı; o da kapanırsa bilgi ver
                dialog.setOnDismissListener {
                    Toast.makeText(this, R.string.update_restarting, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ------------------------------------------------------------------
    // Diyaloglar
    // ------------------------------------------------------------------

    private fun showOfflineInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.offline_info_title)
            .setMessage(R.string.offline_info_msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showLegalNoticeDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.legal_title))
            .setMessage(getString(R.string.legal_text))
            .setPositiveButton(getString(R.string.legal_accept)) { _, _ -> }
            .setCancelable(false)
            .show()
    }

    /** Seçilen altın noktası için bilgi diyaloğunu gösterir. */
    private fun showLocationInfoDialog(loc: GoldLocation) {
        val view = layoutInflater.inflate(R.layout.dialog_location_info, null)

        val tvTitle = view.findViewById<TextView>(R.id.dialog_title)
        val tvConf = view.findViewById<TextView>(R.id.dialog_confidence)
        val tvCoords = view.findViewById<TextView>(R.id.dialog_coords)
        val tvElev = view.findViewById<TextView>(R.id.dialog_elevation)
        val tvTerrain = view.findViewById<TextView>(R.id.dialog_terrain)
        val tvDist = view.findViewById<TextView>(R.id.dialog_distance)
        val tvSource = view.findViewById<TextView>(R.id.dialog_source)
        val tvLith = view.findViewById<TextView>(R.id.dialog_lithology)
        val tvInd = view.findViewById<TextView>(R.id.dialog_indicators)
        val tvReason = view.findViewById<TextView>(R.id.dialog_reasoning)
        val tvDesc = view.findViewById<TextView>(R.id.dialog_description)
        val btnOpen = view.findViewById<Button>(R.id.dialog_btn_open_maps)
        val btnCenter = view.findViewById<Button>(R.id.dialog_btn_center)
        val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)

        tvTitle.text = loc.name

        // Kanıt güveni rozeti
        tvConf.text = buildString {
            if (loc.rank > 0) append("#").append(loc.rank).append("  ")
            append(loc.confidence.ifBlank { loc.category })
        }
        tvConf.setBackgroundColor(
            when {
                loc.confidence.contains("ÇOK") -> android.graphics.Color.parseColor("#B71C1C")
                loc.confidence.contains("YÜKSEK") -> android.graphics.Color.parseColor("#E65100")
                loc.confidence.contains("ORTA") -> android.graphics.Color.parseColor("#F9A825")
                else -> android.graphics.Color.parseColor("#757575")
            }
        )

        tvCoords.text = buildString {
            append(getString(R.string.label_coords))
            append(": ")
            append(String.format("%.5f", loc.lat))
            append(", ")
            append(String.format("%.5f", loc.lon))
        }
        tvElev.text = getString(R.string.label_elevation) + ": " + loc.elevationM + " m (yaklaşık)"
        tvTerrain.text = getString(R.string.label_terrain) + ": " + loc.terrain
        tvDist.text = getString(R.string.label_distance) + ": " + loc.distanceKm + " km"

        tvSource.text = if (loc.sourceDeposit.isBlank()) "" else "Bilinir cevher: " + loc.sourceDeposit
        tvSource.visibility = if (loc.sourceDeposit.isBlank()) View.GONE else View.VISIBLE
        tvLith.text = if (loc.lithology.isBlank()) "" else "Litoloji: " + loc.lithology
        tvLith.visibility = if (loc.lithology.isBlank()) View.GONE else View.VISIBLE
        tvInd.text = if (loc.indicators.isBlank()) "" else "Aranacak göstergeler: " + loc.indicators
        tvInd.visibility = if (loc.indicators.isBlank()) View.GONE else View.VISIBLE
        tvReason.text = if (loc.reasoning.isBlank()) "" else "Jeolojik değerlendirme: " + loc.reasoning
        tvReason.visibility = if (loc.reasoning.isBlank()) View.GONE else View.VISIBLE
        tvDesc.text = loc.description

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnOpen.setOnClickListener {
            openInGoogleMaps(loc.lat, loc.lon)
            dialog.dismiss()
        }
        btnCenter.setOnClickListener {
            mapView.controller.animateTo(GeoPoint(loc.lat, loc.lon))
            mapView.controller.setZoom(14.0)
            dialog.dismiss()
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /** Kısıtlı/yasaklı bir alan için bilgi diyaloğunu gösterir. */
    private fun showRestrictedInfoDialog(area: RestrictedArea) {
        val view = layoutInflater.inflate(R.layout.dialog_location_info, null)

        val tvTitle = view.findViewById<TextView>(R.id.dialog_title)
        val tvConf = view.findViewById<TextView>(R.id.dialog_confidence)
        val tvCoords = view.findViewById<TextView>(R.id.dialog_coords)
        val tvElev = view.findViewById<TextView>(R.id.dialog_elevation)
        val tvTerrain = view.findViewById<TextView>(R.id.dialog_terrain)
        val tvDist = view.findViewById<TextView>(R.id.dialog_distance)
        val tvSource = view.findViewById<TextView>(R.id.dialog_source)
        val tvLith = view.findViewById<TextView>(R.id.dialog_lithology)
        val tvInd = view.findViewById<TextView>(R.id.dialog_indicators)
        val tvReason = view.findViewById<TextView>(R.id.dialog_reasoning)
        val tvDesc = view.findViewById<TextView>(R.id.dialog_description)
        val btnOpen = view.findViewById<Button>(R.id.dialog_btn_open_maps)
        val btnCenter = view.findViewById<Button>(R.id.dialog_btn_center)
        val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)

        tvTitle.text = area.name
        tvConf.visibility = View.GONE
        tvSource.visibility = View.GONE
        tvLith.visibility = View.GONE
        tvInd.visibility = View.GONE
        tvReason.visibility = View.GONE
        tvCoords.text = "Merkez: " +
            String.format("%.5f", area.lat) + ", " + String.format("%.5f", area.lon)
        tvElev.text = "Yarıçap: " + String.format("%.1f", area.radiusM / 1000.0) +
            " km (yaklaşık sınır)"
        tvTerrain.text = "Statü: " + area.type
        tvDist.text = "Bu alanda maden arama/üretim faaliyeti kısıtlı olabilir."
        tvDesc.text = area.description

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        btnOpen.setOnClickListener {
            openInGoogleMaps(area.lat, area.lon)
            dialog.dismiss()
        }
        btnCenter.setOnClickListener {
            mapView.controller.animateTo(GeoPoint(area.lat, area.lon))
            mapView.controller.setZoom(12.0)
            dialog.dismiss()
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    /**
     * Acilis sirasinda kurulu surum degistiyse uygulama guncellenmis demektir.
     * (Guncelleme sonrasi otomatik yeniden baslatma bu yolla da garanti edilir.)
     */
    private fun detectUpdateOnStart() {
        val prefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST_VERSION, -1)
        val current = UpdateChecker.installedVersionCode(this)
        prefs.edit().putInt(KEY_LAST_VERSION, current).apply()

        if (last != -1 && last != current) {
            android.util.Log.i("UpdateChecker", "Guncelleme tespit edildi: $last -> $current")
            // Eski harita onbellegini temizle
            try {
                Configuration.getInstance().setOsmdroidTileCache(
                    java.io.File(
                        getExternalFilesDir(null) ?: filesDir,
                        "osmdroid/tiles-v$current"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Toast.makeText(
                this,
                "Güncelleme tamamlandı - v" + UpdateChecker.installedVersionName(this),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        } else {
            enableMyLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                enableMyLocation()
            } else {
                Toast.makeText(this, getString(R.string.msg_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            myLocationOverlay.enableMyLocation()
        }
    }

    private fun goToMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Konum izni gerekli.", Toast.LENGTH_SHORT).show()
            return
        }
        val loc = myLocationOverlay.myLocation
        if (loc != null) {
            mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
            mapView.controller.setZoom(14.0)
        } else {
            Toast.makeText(this, getString(R.string.toast_no_gps), Toast.LENGTH_LONG).show()
        }
    }

    /** Koordinata Google Haritalar'da rota oluşturarak açar. */
    private fun openInGoogleMaps(lat: Double, lon: Double) {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=driving"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
            )
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // OSMDroid 6.1'de MapView.onDestroy() kaldırılmıştır, onDetach() kullanılır.
        mapView.onDetach()
    }

    companion object {
        // OSMDroid teşhis logları (karo arşivden geliyor mu?). Sorun giderme için true yapılabilir.
        const val DEBUG_MODE = false
        // Guncellendikten sonra tespit icin saklanan son surum kodu
        private const val KEY_LAST_VERSION = "last_version_code"
    }
}
