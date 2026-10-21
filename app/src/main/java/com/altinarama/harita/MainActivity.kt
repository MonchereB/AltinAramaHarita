package com.altinarama.harita

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
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

        // OSMDroid yapılandırması (MapView inflate edilmeden önce yapılmalı)
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        // OSMDroid teşhis logları (karo arşivden geliyor mu?) - sorun giderme için true yapılabilir
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
        // Atif metni: cevrimdisi modda OpenTopoMap, cevrimici modda OpenStreetMap
        findViewById<TextView>(R.id.tvAttribution).text = if (tileResult.isOffline) {
            getString(R.string.attribution)
        } else {
            getString(R.string.attribution_online)
        }
        // Not: setBuiltInZoomControls 6.1'de deprecated; ZoomController kullanılır.
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        mapView.setMultiTouchControls(true)

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
        findViewById<Button>(R.id.btnLegalNotice).setOnClickListener {
            showLegalNoticeDialog()
        }
        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            checkForUpdate(manual = true)
        }

        // Açılışta sessiz güncelleme kontrolü
        findViewById<Button>(R.id.btnUpdate).postDelayed({
            checkForUpdate(manual = false)
        }, 2500)
    }

    // ------------------------------------------------------------------
    // Güncelleme
    // ------------------------------------------------------------------

    /**
     * GitHub deposundaki en son sürümü kontrol eder.
     * @param manual true ise kullanıcı butona bastı (sonuç her zaman gösterilir)
     */
    private fun checkForUpdate(manual: Boolean) {
        val installed = UpdateChecker.installedVersionCode(this)
        UpdateChecker.checkLatest(this) { release, error ->
            if (release == null) {
                if (manual) {
                    Toast.makeText(
                        this,
                        getString(R.string.update_check_failed, error ?: "bilinmeyen hata"),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@checkLatest
            }

            val remote = release.versionCode
            if (remote <= 0 || remote <= installed) {
                // Guncel
                if (manual) {
                    Toast.makeText(
                        this,
                        getString(R.string.update_up_to_date, installed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@checkLatest
            }

            // Yeni surum var
            showUpdateDialog(release, installed)
        }
    }

    private fun showUpdateDialog(release: UpdateChecker.ReleaseInfo, installed: Int) {
        val notes = release.body.take(600).ifBlank { "— kayıt notu yok —" }
        val msg = getString(
            R.string.update_available_msg,
            installed,
            release.versionCode,
            release.tagName,
            notes
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.update_download_btn)) { _, _ ->
                downloadAndInstall(release)
            }
            .setNegativeButton(getString(R.string.update_later_btn), null)
            .show()
    }

    private fun downloadAndInstall(release: UpdateChecker.ReleaseInfo) {
        // Indirme ilerleme penceresi
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        val container = android.widget.FrameLayout(this).apply {
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(
                progress,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val progressDialog = AlertDialog.Builder(this)
            .setView(container)
            .setTitle(getString(R.string.update_downloading, 0))
            .setCancelable(false)
            .create()

        UpdateChecker.downloadApk(
            this,
            release.apkUrl,
            onProgress = { percent ->
                progress.progress = percent
            },
            onDone = { file, err ->
                progressDialog.dismiss()
                if (file == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.update_download_failed, err ?: "?"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@downloadApk
                }
                if (!UpdateChecker.installApk(this, file)) {
                    Toast.makeText(
                        this,
                        getString(R.string.update_install_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        progressDialog.show()
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

        // Kanıt g��veni rozeti
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
        val tvCoords = view.findViewById<TextView>(R.id.dialog_coords)
        val tvElev = view.findViewById<TextView>(R.id.dialog_elevation)
        val tvTerrain = view.findViewById<TextView>(R.id.dialog_terrain)
        val tvDist = view.findViewById<TextView>(R.id.dialog_distance)
        val tvDesc = view.findViewById<TextView>(R.id.dialog_description)
        val btnOpen = view.findViewById<Button>(R.id.dialog_btn_open_maps)
        val btnCenter = view.findViewById<Button>(R.id.dialog_btn_center)
        val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)

        tvTitle.text = area.name
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
            // Google Maps uygulaması yoksa tarayıcıya düş
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
            )
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
        }
    }

    private fun showLegalNoticeDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.legal_title))
            .setMessage(getString(R.string.legal_text))
            .setPositiveButton(getString(R.string.legal_accept)) { _, _ -> }
            .setCancelable(false)
            .show()
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
        // Afyonkarahisar merkezi koordinatı
        const val AFYON_LAT = 38.756886
        const val AFYON_LON = 30.538704
        // OSMDroid teşhis logları (karo arşivden geliyor mu?). Sorun giderme için true yapılabilir.
        const val DEBUG_MODE = false
    }
}
