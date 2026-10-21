@echo off
REM ============================================================
REM  Altin Arama Harita - Derleme Betigi
REM  APK'yi olusturur ve proje kokune kopyalar.
REM ============================================================
setlocal

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

cd /d "%~dp0"

echo ============================================
echo   ALTIN ARAMA HARITA - DERLEME
echo ============================================
echo.

echo [1/2] APK derleniyor (ilk seferinde biraz surebilir)...
REM SQLite gecici dosyasini temizle (MBTiles derleme artigi)
if exist "app\src\main\assets\offline\afyon.mbtiles-journal" del /q "app\src\main\assets\offline\afyon.mbtiles-journal"
REM ONEMLI: clean - degisen buyuk asset'lerde (MBTiles) artik metADATA bayat kalirsa
REM APK iki kat boyutta uretilir. Temiz derleme bunu onler.
call gradlew.bat clean assembleDebug
if errorlevel 1 (
    echo.
    echo [HATA] DERLEME BASARISIZ. Yukaridaki mesaji kontrol edin.
    echo.
    pause
    exit /b 1
)

echo.
echo [2/2] APK kopyalaniyor...
copy /y "app\build\outputs\apk\debug\app-debug.apk" "AltinAramaHarita.apk" >nul
if errorlevel 1 (
    echo [HATA] APK kopyalanamadi.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   TAMAMLANDI!
echo   APK dosyasi: %~dp0AltinAramaHarita.apk
echo ============================================
echo.
echo   Telefona yuklemek icin:
echo   1) Telefonu USB ile baglayin
echo   2) Komut isteminde su komutu calistirin:
echo        adb install -r "%~dp0AltinAramaHarita.apk"
echo.
pause
