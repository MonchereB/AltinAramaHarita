@echo off
REM ============================================================
REM  Altin Arama Harita - Emulator Baslatma
REM  Sanal Android cihaz olusturur ve baslatir.
REM ============================================================
setlocal

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator;%ANDROID_HOME%\cmdline-tools\latest\bin;%PATH%"

cd /d "%~dp0"

echo ============================================
echo   ALTIN ARAMA HARITA - EMULATOR
echo ============================================
echo.

echo [1/2] AVD olusturuluyor / guncelleniyor...
echo no | avdmanager create avd -n AltinHarita -k "system-images;android-34;default;x86_64" --device "pixel_6" --force
if errorlevel 1 (
    echo [HATA] AVD olusturulamadi. SDK'nin "emulator" ve "system-images" paketlerinin kurulu oldugundan emin olun.
    pause
    exit /b 1
)

echo.
echo [2/2] Emulator baslatiliyor (ilk acilista biraz surebilir)...
emulator -avd AltinHarita -no-snapshot-load

echo.
echo Emulator kapandi. Uygulamayi yuklemek icin ayrica su komutu calistirin:
echo    adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
pause
