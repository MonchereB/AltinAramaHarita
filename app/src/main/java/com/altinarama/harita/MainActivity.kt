package com.altinarama.harita

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
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
        // ⚠️ setTileSource kaynak maxZoom'u MapView'a uygulamaz; CANLI'da Google z23
        // isteklerine "Bad Request" veriyor (mozaik görüntü). Tiketten kapat:
        mapView.setMaxZoomLevel(20.0)
        mapView.setMinZoomLevel(8.0)
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

        // Başlangıç kamera: sistem değişince kaydedilen konuma dön; yoksa Afyon merkezi
        val saved = TileSourceProvider.loadCamera(this)
        if (saved != null && saved.first != 0.0 && saved.second != 0.0) {
            mapView.controller.setZoom(saved.third)
            mapView.controller.setCenter(GeoPoint(saved.first, saved.second))
        } else {
            mapHelper.centerOnAfyon()
        }

        // QA kancası: adb ile "am start --es qa_center lat,lon --ei qa_zoom 19"
        // -> kamera deterministik ayarlanır (UI otomasyon testleri için).
        intent?.getStringExtra("qa_center")?.let { qa ->
            runCatching {
                val p = qa.split(",").map { it.trim().toDouble() }
                mapView.controller.setZoom(intent.getIntExtra("qa_zoom", 17).toDouble())
                mapView.controller.setCenter(GeoPoint(p[0], p[1]))
            }
        }

        // Düğmeler
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.btnCenterMyLocation).setOnClickListener {
            goToMyLocation()
        }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.btnMenu
        ).setOnClickListener {
            showMenu()
        }
        setupStyleToggle()
    }

    // ------------------------------------------------------------------
    // Harita stili değişimi (TOPO / UYDU)
    // ------------------------------------------------------------------

    /** TOPO | UYDU | CANLI çiplerini bağlar ve aktif stili boyar. */
    private fun setupStyleToggle() {
        val chipTopo = findViewById<TextView>(R.id.chipTopo)
        val chipSat = findViewById<TextView>(R.id.chipSat)
        val chipLive = findViewById<TextView>(R.id.chipLive)
        val current = TileSourceProvider.currentStyle(this)
        paintStyleChips(chipTopo, chipSat, chipLive, current)

        chipTopo.setOnClickListener {
            if (current != TileSourceProvider.STYLE_TOPO) {
                switchMapStyle(TileSourceProvider.STYLE_TOPO)
            }
        }
        chipSat.setOnClickListener {
            if (current != TileSourceProvider.STYLE_SAT) {
                switchMapStyle(TileSourceProvider.STYLE_SAT)
            }
        }
        chipLive.setOnClickListener {
            if (current != TileSourceProvider.STYLE_LIVE) {
                switchMapStyle(TileSourceProvider.STYLE_LIVE)
            }
        }
    }

    private fun paintStyleChips(topo: TextView, sat: TextView, live: TextView, style: String) {
        for (chip in listOf(topo, sat, live)) {
            val isActive = when (chip) {
                topo -> style == TileSourceProvider.STYLE_TOPO
                sat -> style == TileSourceProvider.STYLE_SAT
                else -> style == TileSourceProvider.STYLE_LIVE
            }
            if (isActive) {
                chip.setBackgroundResource(R.drawable.bg_toggle_active)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_toggle)
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
    }

    /** Stil değişince MapView'ın arşiv dizini yeniden taranmalıdır; recreate gerekli. */
    private fun switchMapStyle(newStyle: String) {
        val center = mapView.mapCenter
        TileSourceProvider.saveCamera(this, center.latitude, center.longitude, mapView.zoomLevelDouble)
        TileSourceProvider.setStyle(this, newStyle)
        Toast.makeText(
            this,
            when (newStyle) {
                TileSourceProvider.STYLE_SAT -> getString(R.string.style_sat_desc) + " yükleniyor…"
                TileSourceProvider.STYLE_LIVE -> getString(R.string.style_live_desc) + " yükleniyor…"
                else -> getString(R.string.style_topo_desc) + " yükleniyor…"
            },
            Toast.LENGTH_SHORT
        ).show()
        recreate()
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

        // Arazide altın rehberi
        view.findViewById<View>(R.id.menuGuide).setOnClickListener {
            sheet.dismiss()
            showFieldGuideDialog()
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

    /** Genel arazi rehberi (altın nerede birikir, savak tekniği, testler). */
    private fun showFieldGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.guide_title))
            .setMessage(getString(R.string.guide_text))
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    /** Kanıt gücü rengine göre renk üretir. */
    private fun confidenceColor(conf: String): Int = when {
        conf.contains("ÇOK") -> Color.parseColor("#B71C1C")
        conf.contains("YÜKSEK") -> Color.parseColor("#E65100")
        conf.contains("ORTA") -> Color.parseColor("#F9A825")
        else -> Color.parseColor("#757575")
    }

    /** Savak uygunluk puanına göre renk. */
    private fun panningColor(grade: String): Int = when {
        grade.contains("ÇOK") -> Color.parseColor("#2E7D32")
        grade.contains("İYİ") || grade.contains("UYGUN") -> Color.parseColor("#43A047")
        grade.contains("ORTA") -> Color.parseColor("#F9A825")
        grade.contains("SINIR") || grade.contains("REVİZE") -> Color.parseColor("#EF6C00")
        else -> Color.parseColor("#757575")
    }

    /** Kazı noktası türünün insan okur etiketi. */
    private fun digTypeLabel(type: String): String = when (type) {
        GoldLocation.DigPoint.CONFLUENCE -> "KAVŞAK"
        GoldLocation.DigPoint.INSIDE_BEND -> "İÇ KIYI"
        else -> "HEDEF"
    }

    /** Seçilen altın noktası için zengin bilgi diyaloğunu gösterir. */
    private fun showLocationInfoDialog(loc: GoldLocation) {
        val view = layoutInflater.inflate(R.layout.dialog_location_info, null)
        val imgHeader = view.findViewById<ImageView>(R.id.imgHeader)
        val badgeRank = view.findViewById<TextView>(R.id.badgeRank)
        val badgeConf = view.findViewById<TextView>(R.id.badgeConf)
        val nameView = view.findViewById<TextView>(R.id.dialog_name)
        val catView = view.findViewById<TextView>(R.id.dialog_category)
        val chipElev = view.findViewById<TextView>(R.id.chipElevation)
        val chipStream = view.findViewById<TextView>(R.id.chipStream)
        val chipSand = view.findViewById<TextView>(R.id.chipSand)
        val chipClay = view.findViewById<TextView>(R.id.chipClay)
        val chipPrecip = view.findViewById<TextView>(R.id.chipPrecip)
        val chipAfyon = view.findViewById<TextView>(R.id.chipAfyon)
        val tvCoords = view.findViewById<TextView>(R.id.dialog_coords)
        val badgePanning = view.findViewById<TextView>(R.id.badgePanning)
        val tvPanningWhy = view.findViewById<TextView>(R.id.dialog_panning_why)
        val tvSource = view.findViewById<TextView>(R.id.dialog_source)
        val tvLith = view.findViewById<TextView>(R.id.dialog_lithology)
        val tvInd = view.findViewById<TextView>(R.id.dialog_indicators)
        val tvReason = view.findViewById<TextView>(R.id.dialog_reasoning)
        val tvFieldTips = view.findViewById<TextView>(R.id.dialog_field_tips)
        val digContainer = view.findViewById<LinearLayout>(R.id.digPointContainer)
        val tvDigNone = view.findViewById<TextView>(R.id.dialog_dig_none)
        val tvSuccess = view.findViewById<TextView>(R.id.dialog_success_checks)
        val tvEvidence = view.findViewById<TextView>(R.id.dialog_evidence)
        val tvAccess = view.findViewById<TextView>(R.id.dialog_access)
        val tvWeather = view.findViewById<TextView>(R.id.dialog_weather)
        val tvDesc = view.findViewById<TextView>(R.id.dialog_description)
        val btnOpen = view.findViewById<Button>(R.id.dialog_btn_open_maps)
        val btnCenter = view.findViewById<Button>(R.id.dialog_btn_center)
        val btnClose = view.findViewById<Button>(R.id.dialog_btn_close)

        // Diyalog erken olusturulur: kazı noktalarindaki "GOSTER" butonu buna erisir.
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        // --- Başlık görseli ---
        nameView.text = loc.name
        catView.text = buildString {
            if (loc.category.isNotBlank()) append(loc.category)
            if (loc.sourceDeposit.isNotBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(loc.sourceDeposit)
            }
        }
        badgeRank.text = if (loc.rank > 0) "#${loc.rank}" else "•"
        badgeConf.text = loc.confidence.ifBlank { "REFERANS" }
        badgeConf.setBackgroundColor(confidenceColor(badgeConf.text.toString()))

        loadHeaderThumb(loc, imgHeader)

        // --- Bilgi çipleri ---
        chipElev.text = "Rakım: ${loc.elevationM} m"
        if (loc.streamRegime.isNotBlank() || loc.streamName.isNotBlank()) {
            chipStream.text = buildString {
                append("Akarsu: ")
                append(loc.streamName.ifBlank { if (loc.streamRegime.isBlank()) "—" else "" })
                if (loc.streamRegime.isNotBlank()) {
                    append(if (length > 8) " · " else "")
                    append(loc.streamRegime)
                }
            }.trimEnd(':', ' ')
        } else {
            chipStream.visibility = View.GONE
        }
        if (loc.sandPct >= 0) chipSand.text = "Kum %${loc.sandPct.toInt()}" else chipSand.visibility = View.GONE
        if (loc.clayPct >= 0) chipClay.text = "Kil %${loc.clayPct.toInt()}" else chipClay.visibility = View.GONE
        if (loc.annualPrecipMm >= 0) {
            chipPrecip.text = "Yağış ${loc.annualPrecipMm} mm/yıl"
        } else chipPrecip.visibility = View.GONE
        chipAfyon.text = "Afyon: ${loc.distanceKm} km"

        tvCoords.text = String.format("%.5f, %.5f", loc.lat, loc.lon)

        // --- Savak uygunluğu ---
        if (loc.panningGrade.isNotBlank()) {
            badgePanning.text = loc.panningGrade
            badgePanning.setBackgroundColor(panningColor(loc.panningGrade))
            tvPanningWhy.text = loc.panningWhy
        } else {
            badgePanning.visibility = View.GONE
        }

        // --- Geoloji ---
        tvSource.text = if (loc.sourceDeposit.isBlank()) "" else "Bilinen cevher: " + loc.sourceDeposit
        tvSource.visibility = if (loc.sourceDeposit.isBlank()) View.GONE else View.VISIBLE
        tvLith.text = if (loc.lithology.isBlank()) "" else "Litoloji: " + loc.lithology
        tvLith.visibility = if (loc.lithology.isBlank()) View.GONE else View.VISIBLE
        tvInd.text = if (loc.indicators.isBlank()) "" else "Aranacak göstergeler: " + loc.indicators
        tvInd.visibility = if (loc.indicators.isBlank()) View.GONE else View.VISIBLE
        tvReason.text = loc.reasoning
        tvReason.visibility = if (loc.reasoning.isBlank()) View.GONE else View.VISIBLE

        // --- Kazı noktaları + saha ipuçları ---
        tvFieldTips.text = loc.fieldTips
        tvFieldTips.visibility = if (loc.fieldTips.isBlank()) View.GONE else View.VISIBLE
        if (loc.digPoints.isEmpty()) {
            tvDigNone.visibility = View.VISIBLE
        } else {
            tvDigNone.visibility = View.GONE
            for (dp in loc.digPoints) {
                val row = layoutInflater.inflate(R.layout.view_dig_point, digContainer, false)
                val title = row.findViewById<TextView>(R.id.digTitle)
                val why = row.findViewById<TextView>(R.id.digWhy)
                val show = row.findViewById<TextView>(R.id.btnShow)
                title.text = buildString {
                    append("• ")
                    append(digTypeLabel(dp.type))
                    if (dp.distM > 0) append("  (≈${dp.distM} m)")
                    if (dp.title.isNotBlank()) append("  ${dp.title}")
                }
                why.text = dp.why
                show.setOnClickListener {
                    mapHelper.showDigPointMarker(dp)
                    dialog.dismiss()
                }
                digContainer.addView(row)
            }
        }

        // --- Kontroller / kanıt / ulaşım ---
        tvSuccess.text = loc.successChecks
        tvSuccess.visibility = if (loc.successChecks.isBlank()) View.GONE else View.VISIBLE
        tvEvidence.text = loc.evidence
        tvEvidence.visibility = if (loc.evidence.isBlank()) View.GONE else View.VISIBLE
        tvAccess.text = loc.access
        tvAccess.visibility = if (loc.access.isBlank()) View.GONE else View.VISIBLE

        // --- Hava durumu (yalnız internet varsa) ---
        tvWeather.text = getString(R.string.panel_weather_loading)
        WeatherFetcher.fetch(loc.lat, loc.lon) { text ->
            runOnUiThread {
                tvWeather.text = text ?: getString(R.string.panel_weather_offline)
            }
        }

        // --- Açıklama ---
        tvDesc.text = buildString {
            if (loc.terrain.isNotBlank()) {
                append("Arazi: ")
                append(loc.terrain)
                append("\n")
            }
            append(loc.description)
        }

        btnOpen.setOnClickListener {
            openInGoogleMaps(loc.lat, loc.lon)
            dialog.dismiss()
        }
        btnCenter.setOnClickListener {
            mapView.controller.animateTo(GeoPoint(loc.lat, loc.lon))
            // UYDU/CANLI'da z17 derin karo bolgesine in; TOPO'da 15 yeterli
            val targetZoom = if (TileSourceProvider.currentStyle(this) ==
                TileSourceProvider.STYLE_TOPO) 15.0 else 17.0
            mapView.controller.setZoom(targetZoom)
            dialog.dismiss()
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /** assets'taki uydu küçük resmini arka planda dekoder edip ImageView'a koyar. */
    private fun loadHeaderThumb(loc: GoldLocation, imgHeader: ImageView) {
        if (loc.thumb.isBlank()) return
        Thread {
            try {
                assets.open(loc.thumb).use { input ->
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        imgHeader.post {
                            imgHeader.setImageBitmap(bmp)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "thumbnail: ${e.message}")
            }
        }.start()
    }

    /** Kısıtlı/yasaklı bir alan için bilgi diyaloğunu gösterir. */
    private fun showRestrictedInfoDialog(area: RestrictedArea) {
        val view = layoutInflater.inflate(R.layout.dialog_restricted_info, null)

        val tvTitle = view.findViewById<TextView>(R.id.ral_dialog_title)
        val tvStatus = view.findViewById<TextView>(R.id.ral_status)
        val tvCoords = view.findViewById<TextView>(R.id.ral_coords)
        val tvRadius = view.findViewById<TextView>(R.id.ral_radius)
        val tvNote = view.findViewById<TextView>(R.id.ral_note)
        val tvDesc = view.findViewById<TextView>(R.id.ral_desc)
        val btnOpen = view.findViewById<Button>(R.id.ral_btn_open_maps)
        val btnCenter = view.findViewById<Button>(R.id.ral_btn_center)
        val btnClose = view.findViewById<Button>(R.id.ral_btn_close)

        tvTitle.text = area.name
        tvStatus.text = "Statü: " + area.type
        tvCoords.text = "Merkez: " +
            String.format("%.5f", area.lat) + ", " + String.format("%.5f", area.lon)
        tvRadius.text = "Yarıçap: " + String.format("%.1f", area.radiusM / 1000.0) +
            " km (yaklaşık sınır)"
        tvNote.text = "Bu alanda maden arama/üretim faaliyeti kısıtlı olabilir."
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
