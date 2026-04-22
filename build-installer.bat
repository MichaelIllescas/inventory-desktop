@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0"
echo Script iniciado. No cierres esta ventana.
echo.

if defined MAVEN_HOME set "PATH=%MAVEN_HOME%\bin;%PATH%"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if not defined JAVA_HOME for /f "tokens=2 delims==" %%a in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr "java.home"') do set "JAVA_HOME=%%a"
if defined JAVA_HOME if "!JAVA_HOME:~-4!"=="\jre" set "JAVA_HOME=!JAVA_HOME:~0,-4!"

set "RUNTIME_JRE=target\runtime-jre"
set "FX_VERSION=21.0.2"
set "FX_JMODS_ZIP=openjfx-%FX_VERSION%_windows-x64_bin-jmods.zip"
set "FX_JMODS_URL=https://download2.gluonhq.com/openjfx/%FX_VERSION%/%FX_JMODS_ZIP%"
set "FX_CACHE=target\fx-jmods"

echo ============================================
echo  Instalador .EXE - Sistema de Inventario
echo ============================================
echo.

where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven no esta en el PATH. Configura MAVEN_HOME o el PATH.
    goto :fin
)

set "JPKG=%JAVA_HOME%\bin\jpackage.exe"
set "JLINK=%JAVA_HOME%\bin\jlink.exe"
if not defined JAVA_HOME set "JPKG=jpackage.exe"
if not defined JAVA_HOME set "JLINK=jlink.exe"
if not exist "%JPKG%" (
    where jpackage >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        for /f "tokens=*" %%a in ('where jpackage 2^>nul') do set "JPKG=%%a"
        for %%b in ("!JPKG!\..\..") do set "JAVA_HOME=%%~fb"
        set "JLINK=!JAVA_HOME!\bin\jlink.exe"
    )
)
if not exist "%JPKG%" (
    echo [ERROR] jpackage no encontrado. Necesitas un JDK 17 o superior ^(no un JRE^).
    echo.
    echo   Define JAVA_HOME con la carpeta de tu JDK, por ejemplo:
    echo   set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21"
    echo   set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    echo.
    echo   Esa carpeta debe contener bin\jpackage.exe y bin\jlink.exe
    goto :fin
)

echo [1/5] Compilando y preparando JARs (Maven)...
call mvn clean package -Pinstaller
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo la compilacion con Maven.
    goto :fin
)

set "APP_JAR=inventory-app-2.0.0.jar"
set "APP_DIR=target\app"
set "OUT_DIR=target\installer"

if not exist "%APP_DIR%\%APP_JAR%" (
    echo [ERROR] No se encontro %APP_DIR%\%APP_JAR%
    goto :fin
)

if exist "%APP_DIR%\data" (
    rmdir /s /q "%APP_DIR%\data"
    echo   Carpeta data eliminada para no incluir datos de desarrollo en el instalador.
)
echo [2/5] JavaFX para el runtime (para que la app instalada abra)...
set "FX_JMODS="
if exist "%JAVA_HOME%\jmods\javafx.controls.jmod" set "FX_JMODS=%JAVA_HOME%\jmods"
if not defined FX_JMODS if exist "%FX_CACHE%\javafx.controls.jmod" set "FX_JMODS=%FX_CACHE%"
if not defined FX_JMODS for /d %%D in ("%FX_CACHE%\*") do if not defined FX_JMODS if exist "%%D\javafx.controls.jmod" set "FX_JMODS=%%D"
if not defined FX_JMODS (
    echo   Descargando JavaFX jmods una vez...
    if not exist "%FX_CACHE%" mkdir "%FX_CACHE%"
    set "FX_ZIP=%~dp0target\%FX_JMODS_ZIP%"
    if not exist "!FX_ZIP!" powershell -NoProfile -Command "Invoke-WebRequest -Uri '%FX_JMODS_URL%' -OutFile '!FX_ZIP!' -UseBasicParsing"
    if not exist "!FX_ZIP!" (
        echo [ERROR] No se pudo descargar. Instala Liberica JDK 21 Full y define JAVA_HOME.
        goto :fin
    )
    powershell -NoProfile -Command "Expand-Archive -Path '!FX_ZIP!' -DestinationPath '%~dp0%FX_CACHE%' -Force"
    if exist "%FX_CACHE%\javafx.controls.jmod" set "FX_JMODS=%FX_CACHE%"
    for /d %%D in ("%FX_CACHE%\*") do if not defined FX_JMODS if exist "%%D\javafx.controls.jmod" set "FX_JMODS=%%D"
)
if not defined FX_JMODS (
    echo [ERROR] No hay jmods de JavaFX. Instala Liberica JDK 21 Full o deja que descargue.
    goto :fin
)
echo   OK.

echo [3/5] Creando runtime con JavaFX (jlink)...
if exist "%RUNTIME_JRE%" rmdir /s /q "%RUNTIME_JRE%"
"%JLINK%" --module-path "%JAVA_HOME%\jmods;%FX_JMODS%" --add-modules java.base,java.logging,java.sql,java.desktop,javafx.controls,javafx.fxml,javafx.graphics,javafx.base --output "%RUNTIME_JRE%" --strip-debug --no-header-files --no-man-pages --compress=2
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo jlink.
    goto :fin
)

echo [4/5] Generando instalador .exe con jpackage...
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set "ICON_PATH=%~dp0src\main\resources\images\logo-nuevo.ico"
set "ICON_OPT="
if exist "%ICON_PATH%" set "ICON_OPT=--icon "%ICON_PATH%""

"%JPKG%" --type exe --name "Sistema de Inventario" --input "%APP_DIR%" --main-jar "%APP_JAR%" --main-class com.ferreteria.App --runtime-image "%RUNTIME_JRE%" --dest "%OUT_DIR%" --app-version 2.0.0 --vendor "Inventario" --description "Sistema de inventario, ventas y reportes" --win-shortcut --win-menu %ICON_OPT%

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo jpackage.
    goto :fin
)

echo [5/5] Listo.
echo.
echo Instalador generado en:
echo   %OUT_DIR%\Sistema de Inventario-2.0.0.exe
echo.
echo Copia ese .exe a cualquier PC con Windows y ejecutalo para instalar.
echo.

:fin
endlocal
echo.
echo ----------------------------------------
echo Si fallo algo, lee el mensaje de error de arriba.
echo Presiona una tecla para cerrar esta ventana.
pause
