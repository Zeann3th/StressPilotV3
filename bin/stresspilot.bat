@echo off
setlocal enabledelayedexpansion

:: StressPilot AppCDS Cache Generator (Windows)
:: Generates app.jsa for faster JVM startup. Run before launching the app.

set "SCRIPT_DIR=%~dp0"
set "APP_ROOT=%SCRIPT_DIR%.."

:: Resolve directories
if "%PILOT_HOME%"=="" (
    set "PLUGINS_DIR=%USERPROFILE%\.pilot\core\plugins"
    set "DRIVERS_DIR=%USERPROFILE%\.pilot\core\drivers"
) else (
    set "PLUGINS_DIR=%PILOT_HOME%\core\plugins"
    set "DRIVERS_DIR=%PILOT_HOME%\core\drivers"
)

:: Search for the executable JAR
set "JAR_FILE="
for /f "delims=" %%i in ('dir /b /s "%APP_ROOT%\target\stresspilot-*-exec.jar" 2^>nul') do (
    set "JAR_FILE=%%i"
    goto :found_jar
)
for /f "delims=" %%i in ('dir /b /s "%APP_ROOT%\stresspilot-*-exec.jar" 2^>nul') do (
    set "JAR_FILE=%%i"
    goto :found_jar
)

:found_jar
if "%JAR_FILE%"=="" (
    echo Error: Could not find stresspilot-*-exec.jar in %APP_ROOT% or %APP_ROOT%\target
    pause
    exit /b 1
)

if "%PILOT_HOME%"=="" (
    set "JSA_DIR=%USERPROFILE%\.pilot\core\scripts"
) else (
    set "JSA_DIR=%PILOT_HOME%\core\scripts"
)
if not exist "%JSA_DIR%" mkdir "%JSA_DIR%"
set "JSA_FILE=%JSA_DIR%\app.jsa"
set "SIG_FILE=%JSA_DIR%\app.sig"

:: Generate Signature
set "JAR_SIZE=0"
for %%A in ("%JAR_FILE%") do set JAR_SIZE=%%~zA

set "PLUGINS_SIG=none"
if exist "%PLUGINS_DIR%" (
    for /f "tokens=*" %%i in ('dir /s /b /a "%PLUGINS_DIR%" 2^>nul ^| find /c /v ""') do set "PLUGINS_COUNT=%%i"
    for /f "tokens=*" %%i in ('dir /s /b /a "%PLUGINS_DIR%" 2^>nul') do set "PLUGINS_SIG=%%i-!PLUGINS_COUNT!"
)

set "DRIVERS_SIG=none"
if exist "%DRIVERS_DIR%" (
    for /f "tokens=*" %%i in ('dir /s /b /a "%DRIVERS_DIR%" 2^>nul ^| find /c /v ""') do set "DRIVERS_COUNT=%%i"
    for /f "tokens=*" %%i in ('dir /s /b /a "%DRIVERS_DIR%" 2^>nul') do set "DRIVERS_SIG=%%i-!DRIVERS_COUNT!"
)

set "CURRENT_SIG=%JAR_SIZE%-!PLUGINS_SIG!-!DRIVERS_SIG!"

:: 1. Check for changes
set "REGENERATE=false"
if not exist "%JSA_FILE%" (
    set "REGENERATE=true"
) else if not exist "%SIG_FILE%" (
    set "REGENERATE=true"
) else (
    set /p OLD_SIG=<"%SIG_FILE%"
    if "!CURRENT_SIG!" neq "!OLD_SIG!" (
        echo Environment change detected. Updating JVM cache...
        set "REGENERATE=true"
    ) else (
        :: Check if JVM rejects the archive
        java -Xshare:on -XX:SharedArchiveFile="%JSA_FILE%" -version >nul 2>&1
        if errorlevel 1 (
            echo JVM version mismatch or incompatible cache. Updating JVM cache...
            set "REGENERATE=true"
        )
    )
)

:: 2. Training
if "!REGENERATE!"=="true" (
    echo Optimizing startup for your environment. This will take a few seconds...
    if exist "%JSA_FILE%" del "%JSA_FILE%"
    java -Dspring.context.exit=onRefresh -XX:ArchiveClassesAtExit="%JSA_FILE%" -jar "%JAR_FILE%" >nul 2>&1
    
    if exist "%JSA_FILE%" (
        echo !CURRENT_SIG!>"%SIG_FILE%"
        echo Optimization complete.
    ) else (
        echo Warning: Failed to generate AppCDS cache.
        exit /b 1
    )
)

endlocal
