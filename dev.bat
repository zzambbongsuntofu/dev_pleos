@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK%"
set "ANDROID_SDK_ROOT=%ANDROID_SDK%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_SDK%\platform-tools;%ANDROID_SDK%\emulator;%PATH%"
set "AVD=Pleos_Connect_v2_1_0"
set "PKG=app.blinddrive"
set "ACTIVITY=app.blinddrive.ui.MainActivity"

if not exist "%ANDROID_SDK%\emulator\emulator.exe" (
  echo Android 에뮬레이터를 찾지 못했습니다.
  echo %ANDROID_SDK%\emulator\emulator.exe
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK를 찾지 못했습니다.
  echo %JAVA_HOME%
  exit /b 1
)

echo Pleos Connect 에뮬레이터를 확인합니다.
adb start-server >nul
adb devices | findstr /i "emulator-" >nul
if errorlevel 1 (
  echo %AVD% 를 시작합니다. 첫 부팅은 몇 분 걸립니다.
  start "Pleos Connect" "%ANDROID_SDK%\emulator\emulator.exe" -avd %AVD%
) else (
  echo 이미 실행 중인 에뮬레이터를 사용합니다.
)

echo 기기가 연결될 때까지 기다립니다.
adb -e wait-for-device

set /a TRIES=0
:wait_boot
set /a TRIES+=1
if %TRIES% GTR 180 (
  echo 부팅 대기 시간이 초과되었습니다. 에뮬레이터 창을 확인하세요.
  exit /b 1
)
set "BOOT="
for /f "delims=" %%B in ('adb -e shell getprop sys.boot_completed') do set "BOOT=%%B"
if not "%BOOT%"=="1" (
  echo 부팅 중... %TRIES%
  timeout /t 5 /nobreak >nul
  goto wait_boot
)

echo 디버그 APK를 빌드합니다.
call gradlew.bat :app:assembleDebug
if errorlevel 1 exit /b 1

echo 앱을 설치합니다.
adb -e install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 exit /b 1

echo 블라인드 드라이브를 실행합니다.
adb -e shell am start -n %PKG%/%ACTIVITY%
if errorlevel 1 exit /b 1
timeout /t 3 /nobreak >nul
adb -e shell am start -n %PKG%/%ACTIVITY%

echo 오른쪽 큰 화면에 블라인드 드라이브를 열었습니다. 왼쪽은 차량 화면입니다.
echo 그 화면이 비어 있으면 아래 앱 목록에서 블라인드 드라이브를 누르세요.
echo 차량 신호가 없으면 화면은 상태 불명으로 열립니다.
exit /b 0
